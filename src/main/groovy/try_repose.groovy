import groovy.json.JsonOutput
import groovy.json.JsonSlurper

/**
 * User: dimi5963
 * Date: 2/22/14
 * Time: 3:36 PM
 */

/**
 1. validate that configurations have been uploaded and repose has been uploaded
 2. upload configuration list
 3. get system-model.cfg.xml and retrieve all filters
    a. for each filter
        1. set up configuration
        2. start repose
        3. log:
           - request
           - response
           - handling list
           - orphaned handling list
    b. stop repose
    c. set request to response
    d. repeat steps a until no filters are left
 4. send response back to user
*/

ReposeValidator validator = new ReposeValidator()
def output = validator.validateArguments(args, [:]) << validator.validateUploadedReposeConfigs([:])

if(output.size() > 0){
    println JsonOutput.prettyPrint(JsonOutput.toJson(output))
} else {
    new Repose().run(args[1], args[0], new JsonSlurper().parseText(args[2]), args[3])
}

