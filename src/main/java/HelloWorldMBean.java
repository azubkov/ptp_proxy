/**
 * Created by IntelliJ IDEA.
 * User: art
 * Date: 15.05.2011
 * Time: 16:37:02
 * To change this template use File | Settings | File Templates.
 */
public interface HelloWorldMBean {
    public void setGreeting( String greeting );
    public String getGreeting();
    public void printGreeting();
}
