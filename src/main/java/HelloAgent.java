import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.ObjectName;


import com.sun.jdmk.comm.*;

/**
 * Created by IntelliJ IDEA.
 * User: art
 * Date: 15.05.2011
 * Time: 16:50:57
 * To change this template use File | Settings | File Templates.
 */
public class HelloAgent
    {

        Money m = Money.dollars(0);
        private MBeanServer mbs = null;
        public HelloAgent()
        {
            mbs = MBeanServerFactory.createMBeanServer("HelloAgent");
            HtmlAdaptorServer adapter = new HtmlAdaptorServer();
            HelloWorld hw = new HelloWorld();
            ObjectName adapterName = null;
            ObjectName helloWorldName = null;
            try {
                helloWorldName =
                        new ObjectName("HelloAgent:name=helloWorld1");
                mbs.registerMBean(hw, helloWorldName);
                adapterName =
                        new ObjectName("HelloAgent:name=htmladapter,port=9092");
                adapter.setPort(9092);
                mbs.registerMBean(adapter, adapterName);
                adapter.start();
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }

    public static void main(String args[]) {
        System.out.println("HelloAgent is running");
        HelloAgent agent = new HelloAgent();
    }
}//class