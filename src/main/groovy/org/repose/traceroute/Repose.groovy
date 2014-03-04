package org.repose.traceroute

import groovy.json.JsonBuilder
import org.apache.commons.io.FileUtils
import org.apache.http.client.ClientProtocolException
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.DefaultHttpClient
import org.linkedin.util.clock.SystemClock
import org.rackspace.deproxy.Deproxy
import org.rackspace.deproxy.Handling
import org.rackspace.deproxy.HeaderCollection
import org.rackspace.deproxy.MessageChain
import org.w3c.dom.Document
import org.w3c.dom.NodeList

import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import java.nio.charset.Charset

import static org.linkedin.groovy.util.concurrent.GroovyConcurrentUtils.waitForCondition

/**
 * User: dimi5963
 * Date: 11/17/13
 * Time: 5:08 PM
 */
class Repose {
    Map configurationMap = [
            'content-normalization' : 'content-normalization.cfg.xml',
            'header-normalization' : 'header-normalization.cfg.xml',
            'uri-normalization' : 'uri-normalization.cfg.xml',
            'client-auth' : 'client-auth-n.cfg.xml',
            'compression' : 'compression.cfg.xml',
            'destination-router' : 'destination-router.cfg.xml',
            'header-id-mapping' : 'header-id-mapping.cfg.xml',
            'http-logging' : 'http-logging.cfg.xml',
            'ip-identity' : 'ip-identity.cfg.xml',
            'rate-limiting' : 'rate-limiting.cfg.xml',
            'service-authentication' : 'service-authentication.cfg.xml',
            'translation' : 'translation.cfg.xml',
            'url-identity' : 'url-identity.cfg.xml',
            'uri-stripper' : 'uri-stripper.cfg.xml',
            'validator' : 'validator.cfg.xml',
            'versioning' : 'versioning.cfg.xml'
    ]

    def  run(String method, String uri, Map headers, String body){
        def repose_output = getRoot(method, uri, headers, body)
        for(Filter filter: retrieveFilterList()) {
            cleanTargetConfigDirectory()
            copyCommonConfigsIntoTargetDirectory()
            setupContainerConfiguration()
            //only for one target right now!
            setupSystemModelConfiguration(filter)
            setupFilterConfiguration(filter)
            Deproxy deproxy = new Deproxy()
            def process
            try{
                process = startRepose(deproxy)
                MessageChain mc = makeRequest(deproxy, uri, method, headers, body)
                def handlings = getHandlings(mc)
                def request_headers_map = [:]
                if(handlings){
                    def result = updateHandlingResults(handlings, filter, repose_output)
                    request_headers_map = result['request_headers_map']
                    repose_output = result['output']
                }else{
                    repose_output = updateHandlingResultsWithError(mc, filter, getResponseMap(mc), repose_output)
                    break
                }
                repose_output = updateResults(mc, getResponseMap(mc), repose_output)
                validateChainIsValid(handlings)
                uri = handlings[handlings.size() - 1].request.path
                method = handlings[handlings.size() - 1].request.method
                headers.clear()
                headers.putAll(request_headers_map)
                body = handlings[handlings.size() - 1].request.body

            }finally{
                stopRepose(deproxy, process)
            }
        }

        return repose_output
    }

    private def getRoot(String method, String uri, Map headers, String body){
        JsonBuilder json = new JsonBuilder()
        return json.repose {
            request (
                    method: method,
                    url: uri,
                    headers: headers,
                    body: body
            )
            filters ([])
        }
    }

    private def cleanTargetConfigDirectory(){
        new File("${System.getProperty("user.dir")}/repose_home/configs").eachFile {
            it.delete()
        }
    }

    private def copyCommonConfigsIntoTargetDirectory(){
        FileUtils.copyDirectory(
                new File("${System.getProperty("user.dir")}/common_configs"),
                new File("${System.getProperty("user.dir")}/repose_home/configs"))
    }

    private def setupSystemModelConfiguration(Filter filter){
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

        return systemModel
    }

    private def setupContainerConfiguration(){
        def container = new File(
                "${System.getProperty("user.dir")}/repose_home/configs/container.cfg.xml")
        container.write "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "\n" +
                "<repose-container xmlns='http://docs.rackspacecloud.com/repose/container/v2.0'>\n" +
                "  <deployment-config client-request-logging=\"true\">\n" +
                "     <deployment-directory auto-clean=\"true\">${System.getProperty("user.dir")}/repose_home/deployments</deployment-directory>" +
                "     <artifact-directory check-interval=\"1000\">${System.getProperty("user.dir")}/usr/share/repose/filters</artifact-directory>" +
                "     <logging-configuration href=\"log4j.properties\" />" +
                "  </deployment-config>" +
                "</repose-container>"

        return container
    }

    private def setupFilterConfiguration(Filter filter){
        File filterFile = new File("${System.getProperty("user.dir")}/imported_configs/").listFiles().find {
            configurationMap[filter.name] == it.name
        }

        new File("${System.getProperty("user.dir")}/imported_configs/").listFiles().findAll {
            it.name.contains(".xsl")
        }.each {

            FileUtils.copyFile(
                    it,
                    new File("${System.getProperty("user.dir")}/repose_home/configs/${it.name}")
            )
        }

        FileUtils.copyFile(
                filterFile,
                new File("${System.getProperty("user.dir")}/repose_home/configs/${configurationMap[filter.name]}"))
    }

    private def startRepose(Deproxy deproxy){
        deproxy.addEndpoint(10001)

        def cmd = """java -jar ${System.getProperty("user.dir")}/usr/share/repose/repose-valve.jar -c ${System.getProperty("user.dir")}/repose_home/configs/ -s 7777 start"""
        println "java -jar ${System.getProperty("user.dir")}/usr/share/repose/repose-valve.jar -c ${System.getProperty("user.dir")}/repose_home/configs/ -s 7777 start"
        def process = cmd.execute()
        SystemClock clock = SystemClock.instance()

        waitForCondition(clock, "60s", "3s") {
            try {
                HttpClient client = new DefaultHttpClient()
                client.execute(new HttpGet("http://localhost:8888")).statusLine.statusCode != 500
            } catch (IOException ignored) {
            } catch (ClientProtocolException ignored) {
            }
        }
        return process
    }

    //DANGER!  WILL KILL ALL JAVA PROCESSES!  ONLY RUN ON A SANDBOX!
    private def stopRepose(Deproxy deproxy, Process process){
        deproxy.shutdown()
        try {
            SystemClock clock = SystemClock.instance()
            Socket s = new Socket()
            s.setSoTimeout(5000)
            s.connect(new InetSocketAddress("localhost", 7777), 5000)
            s.outputStream.write("\r\n".getBytes(Charset.forName("US-ASCII")))
            s.outputStream.flush()
            s.close()

            waitForCondition(clock, "60s", '1s', {
                !isUp()
            })

        } catch (Exception) {
            process.waitForOrKill(5000)
            def execution = "killall java"
            println execution
            def proc = """${execution}""".execute()
        }
    }

    private def makeRequest(Deproxy deproxy, String uri, String method, Map headers, String body){
        println "$uri, $method, $headers,$body"
        println "results: $headers.getClass()"
        return deproxy.makeRequest(
                url: "http://localhost:8888" + uri,
                method: method,
                headers: headers,
                requestBody: body
        )
    }

    private def getHandlings(MessageChain mc){
        if(mc.handlings.size() == 0 && mc.orphanedHandlings.size() > 0){
            return mc.orphanedHandlings
        } else if(mc.handlings.size() > 0) {
            return mc.handlings
        }
        return null
    }

    private def getResponseMap(MessageChain mc){
        Map response_map = [:]
        mc.receivedResponse.headers.get_headers().collect {
            header ->
                response_map.putAt(header.name.toString(), header.value.toString())
        }
        return response_map
    }

    private def updateHandlingResults(List<Handling> handling_requests, Filter filter, output){
        JsonBuilder json = new JsonBuilder()
        HashMap request_headers_map = new HashMap()
        handling_requests[handling_requests.size() - 1].request.headers.get_headers().collect {
            header ->
                if(!header.name.equals("deproxy-request-id") &&
                        !header.name.equals("via") &&
                        !header.name.equals("host") &&
                        !header.name.equals("user-agent") &&
                        !header.name.equals("Transfer-Encoding") &&
                        !header.name.equals("Connection")
                ){
                    request_headers_map.putAt(header.name.toString(), header.value.toString())
                }
        }

        handling_requests.each {
            handling ->
                output['repose']['filters'].add(json.filter {
                    name filter.name
                    content_sent_from_filter (
                            method: handling.request.method,
                            url: handling.request.path,
                            headers: request_headers_map,
                            body: handling.request.body
                    )
                })
        }
        return [output: output, request_headers_map: request_headers_map]
    }

    private def updateHandlingResultsWithError(MessageChain mc, Filter filter, Map response_map, output){
        output['repose'].putAt(
                "response",
                [
                        'headers': response_map,
                        'body': mc.receivedResponse.body,
                        'message': mc.receivedResponse.message,
                        'code': mc.receivedResponse.code,
                        'filter_failed_on': filter.name
                ])
        return output;
    }

    private def updateResults(MessageChain mc, Map response_map, output){
        output['repose'].putAt(
                "response",
                [
                        'headers': response_map,
                        'body': mc.receivedResponse.body,
                        'message': mc.receivedResponse.message,
                        'code': mc.receivedResponse.code
                ])
        return output
    }

    private def validateChainIsValid(handling_requests){
        assert Integer.parseInt(handling_requests[handling_requests.size() - 1].response.code) < 400
    }

    private def retrieveFilterList() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();

        Document doc = builder.parse("${System.getProperty("user.dir")}/imported_configs/system-model.cfg.xml");

        NodeList nodeList = doc.getElementsByTagName("filter");
        return nodeList.collect {
            String path = it.getAttributes().getNamedItem("configuration") != null ?
                it.getAttributes().getNamedItem("configuration").getNodeValue() :
                null;
            String regex = it.getAttributes().getNamedItem("uri-regex") != null ?
                it.getAttributes().getNamedItem("uri-regex").getNodeValue() :
                null;
            String name = it.getAttributes().getNamedItem("name").getNodeValue();
            new Filter(name: name, configPath: path, uriRegex: regex)
        }
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
