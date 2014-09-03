import java.util.HashMap;
import java.util.Map;

/**
 * Created by Jeppe on 24/07/2014.
 */
public class ArgumentsBuilder {
    private Map map = new HashMap<String, String>();

    public ArgumentsBuilder(String[] args) {
        for (int i = 0; i < args.length; i++) {
            int future = i + 1;
            if (args[i].startsWith("-") && future < args.length && !args[future].startsWith("-")) {
                this.map.put(args[i], args[future]);
                i++;
            } else {
                this.map.put(args[i], null);
            }
        }
    }

    public String getItem(String key) {
        return (String) this.map.get(key);
    }

    public boolean hasItem(String key) {
        return this.map.containsKey(key);
    }
}
