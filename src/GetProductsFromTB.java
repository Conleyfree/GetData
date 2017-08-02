import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by HandsomeMrChen on 2017/6/23.
 */
public class GetProductsFromTB {

    public static void main(String[] args){
        get("广东特产");
    }

    public static ArrayList<Product> get(String key){
        ArrayList<Product> products = new ArrayList<>();

        try{
            String urlKey = URLEncoder.encode(key, "utf-8");
            String home_url = "https://list.tmall.com/search_product.htm?q=" + urlKey + "&type=p&redirect=notRedirect";

            /* 根据URL获取搜索首页的所有商品 */
            products.addAll(getProductsByURL(home_url));

            /* 天猫搜索商品页面的url首页与后面的页面的Url不同 */
            /* 在页面上获取搜索结果的页面数量 */
            /* 解析Url获取Document对象 */
            Document document = Jsoup.connect(home_url).get();
            String target = document.getElementsByClass("ui-page-skip").get(0).text();

            /* 对含总页数得文本进行解析，前提是这个文本仅有一个数字，且就是总页数
               (天猫是如此，target字符串基本为：“共39页，到第页 确定”)
               */
            String regEx="[^0-9]";
            Pattern p = Pattern.compile(regEx);
            Matcher m = p.matcher(target);
            Integer totalPages = Integer.parseInt(m.replaceAll("").trim());

            System.out.println("fuck：" + totalPages);
            Integer currentIndex = 60;
            for(int i = 2; i <= totalPages; i++){

                currentIndex = (i - 1) * 60;
                String url = "https://list.tmall.com/search_product.htm?s=" + currentIndex +
                        "&q=" + urlKey + "&sort=s&style=g#J_Filter";
                System.out.println("页数：" + i + " ; url:" + url);
                /* 根据URL获取搜索首页的所有商品 */
                products.addAll(getProductsByURL(url));
            }

        }catch (Exception e){
            e.printStackTrace();
        }

        return products;
    }

    private static String getSellerID(Element shop_div){

        Element shop_content = shop_div.getElementsByClass("productShop-name").get(0);
        String shop_href = shop_content.attr("href");
        String contents[] = shop_href.split("[?]");
        String attributes[] = contents[1].split("[&]");
        Map<String, String> attr_val = new HashMap<>();
        for(int i = 0; i < attributes.length; i++){
            String key_val[] = attributes[i].split("[=]");
            attr_val.put(key_val[0], key_val[1]);
        }
        return attr_val.get("user_number_id");
    }

    private static Product createProduct(Element product_div){

        /* 获取商品在天猫上的id */
        String itemid = product_div.attr("data-id");

        /* 获取商品坐在店铺的id */
        Element shop_div = product_div.getElementsByClass("productShop").get(0);
        String sellerid = getSellerID(shop_div);

        /* 获取商品名 */
        Element product_title = product_div.getElementsByClass("productTitle").get(0);
        String product_name = product_title.getElementsByTag("a").get(0).attr("title");

        /* 获取评论数与月销量 */
        Element product_status = product_div.getElementsByClass("productStatus").get(0);
        String commentCount = product_status.getElementsByTag("span").get(1).getElementsByTag("a").get(0).text();
        String salesOfMonth = product_status.getElementsByTag("em").text();

        /* 创建商品对象，并返回 */
        return new Product(itemid, sellerid, product_name, commentCount, salesOfMonth);
    }

    private static ArrayList<Product> getProductsByURL(String url) throws IOException{

        ArrayList<Product> products = new ArrayList<>();

        Boolean isRetry;
        Document document;
        Elements product_divs;
        do{
            /* 解析Url获取Document对象 */
            document = Jsoup.connect(url).get();

            /* 获取网页源码文本内容 */
            product_divs = document.getElementsByClass("product");

            isRetry = product_divs.size() == 0 ? true : false;

        }while(isRetry);

        System.out.println("获取到商品" + product_divs.size() + "件。");
        for(int i = 0 ;i < product_divs.size(); i++){

            /* 解析HTML，构建Product对象 */
            Element product_div = product_divs.get(i);
            Product product = createProduct(product_div);
            System.out.println(product.getName());
            products.add(product);
        }

        return products;
    }

}
