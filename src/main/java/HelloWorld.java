/**
 * Created by IntelliJ IDEA.
 * User: art
 * Date: 15.05.2011
 * Time: 16:39:04
 * To change this template use File | Settings | File Templates.
 */
public class HelloWorld {

    private String greeting = null;

    public HelloWorld() {
        this.greeting = "Hello World! I am a Standard MBean";
    }

    public HelloWorld(String greeting) {
        this.greeting = greeting;
    }

    public void setGreeting(String greeting) {
        this.greeting = greeting;
    }

    public String getGreeting() {
        return greeting;
    }

    public void printGreeting() {
        System.out.println(greeting);
    }

}
