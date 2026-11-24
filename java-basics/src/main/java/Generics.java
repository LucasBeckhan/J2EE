// 泛型是把"类型检查"从运行时提前到编译期的工具
// 泛型不是"消灭类型"，而是"推迟类型决定"——但推迟≠取消，最终还是有人要把类型定下来。

import java.util.ArrayList;
import java.util.List;

public class Generics {
  public static void main(String[] args){
    List<Integer> list = new ArrayList<>();
    list.add(100);
    //list.add("hello");


    System.out.println(list);
  }
}
