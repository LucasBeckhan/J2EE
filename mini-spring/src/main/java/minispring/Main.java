package minispring;

public class Main {
    public static void main(String[] args) throws Exception {
        // 启动容器：读配置 -> 反射实例化 -> 依赖注入，一气呵成
        MiniApplicationContext context = new MiniApplicationContext("beans.properties");

        // 业务代码只向容器"要"，从不自己 new
        UserService userService = context.getBean(UserService.class);
        userService.register(1);

        // 验证单例：两次取出的是同一个对象
        UserDao a = context.getBean(UserDao.class);
        UserDao b = context.getBean(UserDao.class);
        System.out.println("单例验证 a == b : " + (a == b));
    }
}
