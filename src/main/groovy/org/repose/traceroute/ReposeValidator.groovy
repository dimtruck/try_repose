package org.repose.traceroute

import groovy.json.JsonException
import groovy.json.JsonSlurper
import org.xml.sax.SAXException

/**
 * User: dimi5963
 * Date: 2/22/14
 * Time: 4:53 PM
 */
class ReposeValidator {
    def validateUploadedReposeConfigs(output){
        output = validateCommonConfigsExist(output)
        return validateImportedConfigsExist(output)
    }

    def validateArguments(args, output){
        output = validateNumberOfArguments(args, [:])
        if(output.size() > 0){
            return output
        }
        output = validateURIArgument(args[0], output)
        output = validateMethodArgument(args[1], output)
        output = validateHeadersArgument(args[2], output)
        return validateBodyArgument(args[3],args[2], args[1], output)

    }

    def validateNumberOfArguments(args, output){
        if(args == null){
            return updateErrors(output, 'arg_error', "not enough arguments were specificed: $args")
        } else if(args.size() != 4){
            return updateErrors(output, 'arg_error', "invalid number of arguments were specificed: $args")
        }
        return output
    }

    def validateURIArgument(entry, output){
        try{
            new URL("http://localhost:8080$entry")
            return output
        } catch (MalformedURLException){
            return updateErrors(output, 'uri_error', "invalid uri provided: $entry")
        }
    }

    def validateMethodArgument(entry, output){
        if(['GET','POST','PUT','DELETE','PATCH','HEAD'].contains(entry.toUpperCase())){
            return output
        } else {
            return updateErrors(output, 'method_error', "invalid method provided: $entry")
        }
    }

    def validateHeadersArgument(entry, output){
        try {
            new JsonSlurper().parseText(entry)
            return output
        } catch(JsonException je){
            return updateErrors(output, 'header_format_error', "headers must be provided in a json format: $entry")
        }
    }

    def validateBodyArgument(entry, header_json, method, output){
        if(entry.size() == 0)
            return output
        if(['GET','HEAD','DELETE'].contains(method.toUpperCase()))
            return output
        def headers = new JsonSlurper().parseText(header_json)
        if(!headers.containsKey('content-type'))
            return updateErrors(output, 'body_content_type_missing_error',"please provide content-type header for this request")
        if(headers['content-type'] == 'application/json'){
            try {
                new JsonSlurper().parseText(entry)
                return output
            } catch(JsonException je){
                return updateErrors(output, 'body_format_error', "body must be provided in a json format: $entry")
            }
        }else if(headers['content-type'] == 'application/xml'){
            try {
                new XmlSlurper().parseText(entry)
                return output
            } catch(SAXException je){
                return updateErrors(output, 'body_format_error', "body must be provided in a xml format: $entry")
            }
        }else {
            return output
        }

    }

    private def validateCommonConfigsExist(output){
        File commonConfigsDirectory = new File("${System.getProperty("user.dir")}/common_configs")
        if(!commonConfigsDirectory.exists() || commonConfigsDirectory.listFiles() == 0){
            output = updateErrors(output, 'error', 'common configs missing')
            return output
        }
        return output
    }

    private def validateImportedConfigsExist(output){
        File importedConfigsDirectory = new File("${System.getProperty("user.dir")}/imported_configs")
        if(importedConfigsDirectory.exists()){
            if(importedConfigsDirectory.listFiles().find{
                it.name == "system-model.cfg.xml"
            }){
                return output
            } else {
                return updateErrors(output, 'error', 'system model does not exist')
            }
        } else {
            return updateErrors(output, 'error', 'imported configs do not exist')
        }
    }

    private def updateErrors(Map output, String key, String error){
        if(output != null && output.containsKey(key)){
            output[key] << error
        }else{
            output = [:]
            output[key] = [error]
        }
        return output
    }
}
