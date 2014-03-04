package org.repose.traceroute

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

/**
 * User: dimi5963
 * Date: 3/3/14
 * Time: 4:51 PM
 */
class ReposeTraceRouter {
    public static void main(String[] args){
        ReposeValidator validator = new ReposeValidator()
        def output = validator.validateArguments(args, [:]) << validator.validateUploadedReposeConfigs([:])

        if(output.size() > 0){
            println JsonOutput.prettyPrint(JsonOutput.toJson(output))
        } else {
            println JsonOutput.prettyPrint(
                    JsonOutput.toJson(
                            new Repose().run(args[1], args[0], new JsonSlurper().parseText(args[2]), args[3]))
            )
        }
    }
}
