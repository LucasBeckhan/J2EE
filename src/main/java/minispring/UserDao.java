package minispring;

/** 模拟数据访问层，没有任何依赖，作为"最底层"的 Bean */
public class UserDao {

    public String findUserNameById(int id) {
        // 假装查了数据库
        return (id == 1) ? "张三" : "未知用户";
    }
}
