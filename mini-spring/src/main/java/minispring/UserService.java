package minispring;

/**
 * 模拟业务层。注意：
 * - 全程没有 new UserDao()，依赖是容器"塞进来"的
 * - 字段是 private 且没有 setter，容器靠 field.setAccessible(true) 直接赋值
 */
public class UserService {

    @MyAutowired
    private UserDao userDao;   // 编译期只知道类型，实例从哪来？容器说了算

    public void register(int userId) {
        String name = userDao.findUserNameById(userId);
        System.out.println("[UserService] 为用户 " + userId + "（" + name + "）办理注册成功");
    }
}
