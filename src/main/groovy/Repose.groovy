import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import org.apache.commons.io.FileUtils
import org.apache.http.client.ClientProtocolException
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.DefaultHttpClient
import org.linkedin.util.clock.SystemClock
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.rackspace.gdeproxy.Deproxy;
import org.json.JSONObject

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.*;
import java.nio.charset.Charset;
import java.util.concurrent.TimeoutException

import static org.linkedin.groovy.util.concurrent.GroovyConcurrentUtils.waitForCondition;

/**
 * User: dimi5963
 * Date: 11/17/13
 * Time: 5:08 PM
 */
class Repose {
    private Map<String, String> configurationMap = [
            'content-normalization' : 'content-normalization.cfg.xml',
            'header-normalization' : 'header-normalization.cfg.xml',
            'uri-normalization' : 'uri-normalization.cfg.xml',
            'client-auth' : 'client-auth-n.cfg.xml'
    ]

    /**
     * returns {
     *   'request': METHOD uri [header1,header2] body
     *   'filters':[
     *       'filter':{
     *           'name':'filter.xml',
     *           'request': METHOD uri [header1,header2] body,
     *           'internal': [
     *               METHOD uri [header1,header2] body,
     *               METHOD uri [header1,header2] body
     *           ],
     *           'response': json/xml
     *       },
     *       'filter':{
     *           'name':'filter.xml',
     *           'request': METHOD uri [header1,header2] body,
     *           'internal': [
     *               METHOD uri [header1,header2] body,
     *               METHOD uri [header1,header2] body
     *           ],
     *           'response': json/xml
     *       }
     *   ],
     *   'response': json/xml
     * }
     * @param args
     */
    static void main(String[] args){
        //initial request
        String url = args[1]
        String method = args[0]
        String headers = args[2]
        String bodyMessage = args[3]

        JsonSlurper slurper = new JsonSlurper()
        JsonBuilder json = new JsonBuilder()

        def request_headers = slurper.parseText(headers)

        //1. upload configuration list
        //2. get system-model.cfg.xml and retrieve all filters
        //3. for each filter
        //4.   set up configuration
        //5.   start repose
        //6.   log:
        //       - request
        //       - response
        //       - handling list
        //       - orphaned handling list
        //7.   stop repose
        //8.   set request to response
        //9.   repeat steps 4-8 until no filters are left
        Repose repose = new Repose();
        def root = json.repose {
            request (
                    method: method,
                    url: url,
                    headers: request_headers,
                    body: bodyMessage
            )
            filters ([])
        }
        println System.getProperty("user.dir");
        try{
            //#2
            repose.retrieveFilterList();
            //#3
            for(Filter filter : repose.retrieveFilterList()){
                new File("${System.getProperty("user.dir")}/repose_home/configs").eachFile {
                    it.delete()
                }

                //#4 - to set up configuration, you need the following:
                //container.cfg.xml
                FileUtils.copyDirectory(
                        new File("${System.getProperty("user.dir")}/common_configs"),
                        new File("${System.getProperty("user.dir")}/repose_home/configs"))
                File systemModel = new File(
                        "${System.getProperty("user.dir")}/repose_home/configs/system-model.cfg.xml")
                systemModel << '<?xml version="1.0" encoding="UTF-8"?>\n' +
                        '\n' +
                        '<!-- To configure Repose see: http://wiki.openrepose.org/display/REPOSE/Configuration -->\n' +
                        '<system-model xmlns="http://docs.rackspacecloud.com/repose/system-model/v2.0">\n' +
                        '  <repose-cluster id="repose" rewrite-host-header="false">\n' +
                        '    <nodes>\n' +
                        '        <node id="node1" hostname="localhost" http-port="8888"/>\n' +
                        '    </nodes>\n' +
                        '    <filters>\n'

                systemModel << "<filter name=\"${filter.name}\" "

                if (filter.configPath != null) {
                    systemModel << " configuration=\"${filter.configPath}\" "
                }

                if (filter.uriRegex != null) {
                    systemModel << " uri-regex=\"${filter.uriRegex}\" "
                }

                systemModel << '/>\n' +
                        '    </filters>\n' +
                        '    <destinations>\n' +
                        '      <!-- Update this endpoint if you want Repose to send requests to a different service -->\n' +
                        '        <endpoint id="target" protocol="http" hostname="localhost" root-path="/" port="10001" default="true" />\n' +
                        '    </destinations>\n' +
                        '  </repose-cluster>\n' +
                        '</system-model>'

                File filterFile = new File("${System.getProperty("user.dir")}/imported_configs/").listFiles().find {
                    repose.configurationMap[filter.name] == it.name
                }

                FileUtils.copyFile(
                        filterFile,
                        new File("${System.getProperty("user.dir")}/repose_home/configs/${repose.configurationMap[filter.name]}"))

                //#5
                Deproxy deproxy = new Deproxy()
                deproxy.addEndpoint(10001)

                def cmd = """java -jar ${System.getProperty("user.dir")}/usr/share/repose/repose-valve.jar -c ${System.getProperty("user.dir")}/repose_home/configs/ -s 7777 start"""

                def process = cmd.execute()
                SystemClock clock = SystemClock.instance()

                waitForCondition(clock, "60s", "1s") {
                    try {
                        HttpClient client = new DefaultHttpClient()
                        client.execute(new HttpGet("http://localhost:8888")).statusLine.statusCode != 500
                    } catch (IOException ignored) {
                    } catch (ClientProtocolException ignored) {
                    }
                }

                //6 log
                def mc = deproxy.makeRequest(
                        "http://localhost:8888" + url,
                        method,
                        request_headers,
                        bodyMessage
                )

                def handling_requests = null

                if(mc.handlings.size() == 0 && mc.orphanedHandlings.size() > 0){
                    handling_requests = mc.orphanedHandlings
                } else if(mc.handlings.size() > 0) {
                    handling_requests = mc.handlings
                }

                Map response_map = [:]
                mc.receivedResponse.headers.get_headers().collect {
                    header ->
                        response_map.putAt(header.name.toString(), header.value.toString())
                }

                Map request_headers_map = [:]

                if(handling_requests){
                    handling_requests[handling_requests.size() - 1].request.headers.get_headers().collect {
                        header ->
                            if(!header.name.equals("deproxy-request-id"))
                                request_headers_map.putAt(header.name.toString(), header.value.toString())
                    }

                    handling_requests.each {
                        handling ->
                            root['repose']['filters'].add(json.filter {
                                name filter.name
                                content_sent_from_filter (
                                        method: handling.request.method,
                                        url: handling.request.path,
                                        headers: request_headers_map,
                                        body: handling.request.body
                                )
                            })
                    }
                } else {
                    root['repose'].putAt(
                            "response",
                            [
                                    'headers': response_map,
                                    'body': mc.receivedResponse.body,
                                    'message': mc.receivedResponse.message,
                                    'code': mc.receivedResponse.code,
                                    'filter_failed_on': filter.name
                            ])
                    deproxy.shutdown()
                    try {

                        Socket s = new Socket()
                        s.setSoTimeout(5000)
                        s.connect(new InetSocketAddress("localhost", 7777), 5000)
                        s.outputStream.write("\r\n".getBytes(Charset.forName("US-ASCII")))
                        s.outputStream.flush()
                        s.close()

                        waitForCondition(clock, "60s", '1s', {
                            !repose.isUp()
                        })

                    } catch (Exception) {
                        process.waitForOrKill(5000)
                        throw new TimeoutException("Repose failed to stop cleanly")
                    }

                    break

                }

                root['repose'].putAt(
                        "response",
                        [
                                'headers': response_map,
                                'body': mc.receivedResponse.body,
                                'message': mc.receivedResponse.message,
                                'code': mc.receivedResponse.code
                        ])

                assert Integer.parseInt(handling_requests[handling_requests.size() - 1].response.code) < 400
                url = handling_requests[handling_requests.size() - 1].request.path
                method = handling_requests[handling_requests.size() - 1].request.method
                request_headers = request_headers_map
                bodyMessage = handling_requests[handling_requests.size() - 1].request.body


                deproxy.shutdown()
                try {

                    Socket s = new Socket()
                    s.setSoTimeout(5000)
                    s.connect(new InetSocketAddress("localhost", 7777), 5000)
                    s.outputStream.write("\r\n".getBytes(Charset.forName("US-ASCII")))
                    s.outputStream.flush()
                    s.close()

                    waitForCondition(clock, "60s", '1s', {
                        !repose.isUp()
                    })

                } catch (Exception) {
                    process.waitForOrKill(5000)
                    throw new TimeoutException("Repose failed to stop cleanly")
                }
            }
        }catch (Exception e){
            e.printStackTrace();
        } finally{
            println JsonOutput.prettyPrint(JsonOutput.toJson(root))
        }


    }

    private List<Filter> retrieveFilterList()
    throws ParserConfigurationException, IOException, SAXException, XPathExpressionException{
        List<Filter> filterList = new ArrayList<Filter>();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();

        Document doc = builder.parse("${System.getProperty("user.dir")}/imported_configs/system-model.cfg.xml");

        NodeList nodeList = doc.getElementsByTagName("filter");
        for (int i = 0; i < nodeList.getLength(); i++) {
            String path = nodeList.item(i).getAttributes().getNamedItem("configuration") != null ?
                nodeList.item(i).getAttributes().getNamedItem("configuration").getNodeValue() :
                null;
            String regex = nodeList.item(i).getAttributes().getNamedItem("uri-regex") != null ?
                nodeList.item(i).getAttributes().getNamedItem("uri-regex").getNodeValue() :
                null;
            String name = nodeList.item(i).getAttributes().getNamedItem("name").getNodeValue();
            filterList.add(new Filter(name, path, regex));
        }
        return filterList;
    }


    private String getJvmProcesses() {
        def runningJvms = "jps -v".execute()
        runningJvms.waitFor()

        return runningJvms.in.text
    }

    public boolean isUp() {
        return getJvmProcesses().contains("repose-valve.jar")
    }

}
