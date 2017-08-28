import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

/**
 * Created by HandsomeMrChen on 2017/6/24.
 */
public class DataPersistence {

    private static SQLConnection sql_conn;

    static{
        sql_conn = new SQLConnection();
        sql_conn.connSQL();                 // 链接 mysql 数据库
    }

    public static Boolean insertProduct(Product product) throws SQLException {
        if(product == null)
            return  false;
        String sql = "insert into product(item_id,seller_id,name,sales_of_month,comments_count) " +
                "values(" + product.getItemId() + "," + product.getSellerId() + ",'" +
                    product.getName() + "','" + product.getSalesOfMonth() + "','" +
                    product.getCommentsCount() + "')";
        Boolean result = sql_conn.insertSQL(sql);           // 插入记录
        if(result){
            sql = "select LAST_INSERT_ID()";                // 获取自增主键的值
            ResultSet rs = sql_conn.selectSQL(sql);
            rs.next();                                      // ResultSet对象代表SQL语句执行的结果集，维护指向其当前数据行的光标。每调用一次next()方法，光标向下移动一行。最初它位于第一行之前
            System.out.println("pid: " + rs.getInt(1));
            product.setPid(rs.getInt(1));                   // 保存至Product对象
        }
        return result;
    }

    public static Boolean insertCommentsOfProduct(Product product){
        if(product == null)
            return false;
        ArrayList<String> comments = product.getComments();
        Boolean result = true;
        for(String comment : comments){
            String sql = "insert into comment(content,append,pid) " +
                    "value('" + comment + "',''," + product.getPid() + ")";
            result = sql_conn.insertSQL(sql);
            if(!result)
                break;
        }
        return result;
    }

    /* 获取所有 pid 大于传入参数值的Product */
    public static ArrayList<Product> selectProducts(Integer pid) throws SQLException {
        ArrayList<Product> products = new ArrayList<>();
        String sql = "select * from product where pid >= " + pid;
        ResultSet rs = sql_conn.selectSQL(sql);
        while(rs.next()){
            int id = rs.getInt("pid");
            String item_id = rs.getString("item_id");
            String seller_id = rs.getString("seller_id");
            String name = rs.getString("name");
            String sales_of_month = rs.getString("sales_of_month");
            String comments_count = rs.getString("comments_count");
            Product product = new Product(item_id, seller_id, name, comments_count, sales_of_month);
            product.setPid(id);
            products.add(product);
        }
        return products;
    }

    public static void close(){
        sql_conn.deconnSQL();
    }
}
