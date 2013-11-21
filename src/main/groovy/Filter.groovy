/**
 * User: dimi5963
 * Date: 11/17/13
 * Time: 8:00 PM
 */
class Filter {
    private String name
    private String configPath
    private String uriRegex

    Filter(String name, String configPath, String uriRegex){
        this.name = name
        this.configPath = configPath
        this.uriRegex = uriRegex
    }

    String getName(){
        name
    }

    String getConfigPath(){
        configPath
    }

    String getUriRegex(){
        uriRegex
    }
}
