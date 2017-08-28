import java.io.*;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.sql.SQLException;
import java.util.ArrayList;

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.net.ssl.HttpsURLConnection;

/**
 * Created by HandsomeMrChen on 2017/6/21.
 * 从淘宝上获取某商品的评价
 */
public class GetCommentsFromTB {

    private static Logger logger = Logger.getLogger(GetCommentsFromTB.class);
    static String keyword = "礼品";

    public static void main(String[] args) throws SQLException{

        System.out.println("天猫上搜索'" + keyword + "'的所有商品及其评论：");

        ArrayList<Product> products = new ArrayList<>();
        Integer current_pid = 1;
        if(args.length == 0)
            products = GetProductsFromTB.get(keyword);
        else if(args.length == 1){
            /* 从数据库中获取 */
            try{
                current_pid = Integer.parseInt(args[0]);
                products.addAll(DataPersistence.selectProducts(current_pid));
            }catch(NumberFormatException nfe){
                logger.error("参数错误！");
            }
        }else{
            logger.error("参数错误！");
        }

        int pid = current_pid;
        for(Product product : products){

            logger.info("当前搜索商品的主键： pid = " + pid);

            /* 控制台输出商品信息 */
            System.out.println("商品id：" + product.getItemId() + "; 店家id：" + product.getSellerId());
            System.out.println("商品名称：" + product.getName());
            System.out.println("评论数：" + product.getCommentsCount());
            System.out.println("月销量：" + product.getSalesOfMonth());

            /* 获取商品评论，并保存到数据库 */
            product.getAllComments();

            DataPersistence.insertCommentsOfProduct(product);

            pid++;
        }

        /* 关闭 mysql 的连接 */
        DataPersistence.close();

    }

}

class Product{

    private Integer pid;                                                    // 商品插入数据库后的主键值
    private String name;                                                    // 商品名称
    private String commentsCount;                                           // 评论数
    private String salesOfMonth;                                            // 月销量
    private ArrayList<String> comments;                                     // 评论 ps：评论集合的大小不等于commentsCount，原因是当商品评论数过多时（超过99页），过久之前的评论无法获取

    private String home = "https://rate.tmall.com/list_detail_rate.htm";
    private String itemId = "";                                             // 商品关联ID
    private String sellerId = "";                                           // 卖家账号
    private Integer currentPage = 0;                                        // 当前页面
    private Integer order = 0;                                              // 3: 按热度； 其它或删除：按时间

    public String getItemId() {
        return itemId;
    }

    public String getSellerId() {
        return sellerId;
    }

    public String getName() {
        return name;
    }

    public String getCommentsCount() {
        return commentsCount;
    }

    public String getSalesOfMonth() {
        return salesOfMonth;
    }

    public ArrayList<String> getComments() {
        return comments;
    }

    public Integer getPid() {
        return pid;
    }

    void setPid(Integer pid){
        this.pid = pid;
    }

    private static Logger logger = Logger.getLogger(Product.class);

    Product(String itemId, String sellerId, String name, String commentsCount, String salesOfMonth){
        this.itemId = itemId;
        this.sellerId = sellerId;
        this.name = name;
        this.commentsCount = commentsCount;
        this.salesOfMonth = salesOfMonth;
    }

    private String getCurrentPageURL(){
        return home + "?" + "itemId=" + itemId + "&" + "sellerId=" + sellerId +
                "&" + "currentPage=" + currentPage + "&" + "order=" + order;
    }

    private String getCommentsOfCurrentPage() {
        String url = getCurrentPageURL();

        System.out.println("url: " + url);

        String result = "[{";
        try {
            URL comments_request = new URL(url);
            HttpsURLConnection conn_comments_req = (HttpsURLConnection) comments_request.openConnection();

            conn_comments_req.setConnectTimeout(2000);                              // 设置连接超时时间，单位毫秒
            conn_comments_req.setReadTimeout(2000);                                 // 设置读取数据超时时间，单位毫秒
            conn_comments_req.setDoOutput(true);                                    // 是否打开输出流 true|false
            conn_comments_req.setDoInput(true);                                     // 是否打开输入流true|false
            conn_comments_req.setRequestMethod("POST");                             // 提交方法POST|GET
            conn_comments_req.setUseCaches(false);                                  // 是否缓存true|false
            conn_comments_req.setRequestProperty("Content-Type", "text/html;charset=gbk");
            conn_comments_req.setRequestProperty("Accept-Charset", "gbk");

            conn_comments_req.connect();                                            // 打开连接端口

            // 定义BufferedReader输入流来读取URL的响应
            BufferedReader in = new BufferedReader(new InputStreamReader(conn_comments_req.getInputStream(), "gbk"));
            String line;
            while ((line = in.readLine()) != null)
                // result += line;
                result += new String(line.getBytes(), "UTF-8");
            in.close();
            result += "}]";

            /* 强行转换为utf-8编码形式，否则无法转换为JSONArray */
            result = new String(result.getBytes("UTF-8"), "ISO-8859-1");
            result = new String(result.getBytes("ISO-8859-1"), "UTF-8");

        }catch(SocketTimeoutException e){
            logger.info("SocketTimeoutException, 网络中断！");
            try{
                Thread.sleep(12000);
            }catch(InterruptedException ie){
                logger.info("休眠被中断");
            }
            logger.info("继续访问访问！");
            result = getCommentsOfCurrentPage();    // 继续获取

        }catch(ConnectException ce){
            logger.info("ConnectException, 网络中断！");
            try{
                Thread.sleep(12000);
            }catch(InterruptedException ie){
                logger.info("休眠被中断");
            }
            logger.info("继续访问访问！");
            result = getCommentsOfCurrentPage();    // 继续获取

        }catch(Exception e){
            System.out.println(e);
            return "204: can not get the json";
        }

        return result;
    }

    ArrayList<String> getAllComments() {
        ArrayList<String> comments_list = new ArrayList<>();
        int count = 0, totalPages = 0, attemptLimit = 50;
        JSONObject obj;
        do{
            currentPage ++;
            String result = getCommentsOfCurrentPage();              // 获取当前页面的评论
            if(result.startsWith("204")){        // 可能由于频繁访问而短时间无法获取需要的数据
                currentPage --;
                attemptLimit --;
                if(attemptLimit == 0){
                    System.out.println("400：connection is broken！");
                    logger.error("由于过频繁访问而无法获取需要的数据，经过" + attemptLimit + "次尝试未果！");
                    return null;
                }
                continue;
            }
            attemptLimit = 50;

            try {
                // 把字符串转换为JSONArray对象
                JSONArray comment_json = new JSONArray(result);
                if(comment_json.length() > 0){
                    obj = comment_json.getJSONObject(0);
                    obj = (JSONObject) obj.get("rateDetail");

                    JSONObject paginator = (JSONObject) obj.get("paginator");
                    totalPages = paginator.getInt("lastPage");                  // 针对天猫，每次遍历都需要更新最后一页的页码

                    JSONArray comments = obj.getJSONArray("rateList");
                    for(int i = 0; i < comments.length(); i++){
                        count ++;
                        System.out.println(count + ". " + comments.getJSONObject(i).get("rateContent"));
                        comments_list.add((String) comments.getJSONObject(i).get("rateContent"));
                    }
                }else{      // 获取不到JSON
                    break;
                }
            } catch (Exception e) {
                // e.printStackTrace();
                currentPage --;
                System.out.println("204: can not get the json");
            }
        }while(currentPage < totalPages);

        System.out.println("200: success!");
        logger.info("已成功读取当前商品所有评论\n");
        this.comments = comments_list;
        return comments_list;
    }
}
