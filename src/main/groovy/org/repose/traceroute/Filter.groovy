package org.repose.traceroute

import groovy.transform.EqualsAndHashCode

/**
 * User: dimi5963
 * Date: 11/17/13
 * Time: 8:00 PM
 */
@EqualsAndHashCode
class Filter {
    def name
    def configPath
    def uriRegex

    String toString(){
        "($name,$configPath,$uriRegex)"
    }
}
