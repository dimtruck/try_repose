require 'sinatra'
require 'json'
require 'logging'
require 'rbconfig'
require 'open-uri'
require 'zip'

class TryReposeApp < Sinatra::Base
  enable :sessions

  get '/' do
    body 'test'
  end

  get '/version/:version' do |version|
    `rm -rf usr/`
    `virtualenv . ; . bin/activate ; pip install requests ; pip install narwhal ; download-repose --version #{version} ; deactivate`
    status 200
  end

  post '/test' do
    puts request.body.read
    request.body.rewind
    data = JSON.parse request.body.read
    uri = data['uri']
    headers = data['headers'].to_json
    body = data['body']
    method = data['method']

    p `java -classpath "/usr/lib/jvm/java-6-openjdk-amd64/lib/deploy.jar:/usr/lib/jvm/java-6-openjdk-amd64/lib/dt.jar:/usr/lib/jvm/java-6-openjdk-amd64/lib/javaws.jar:/usr/lib/jvm/java-6-openjdk-amd64/lib/jce.jar:/usr/lib/jvm/java-6-openjdk-amd64/lib/jconsole.jar:/usr/lib/jvm/java-6-openjdk-amd64/lib/management-agent.jar:/usr/lib/jvm/java-6-openjdk-amd64/lib/plugin.jar:/usr/lib/jvm/java-6-openjdk-amd64/lib/sa-jdi.jar:/System/Library/Java/JavaVirtualMachines/1.6.0.jdk/Contents/Classes/charsets.jar:/System/Library/Java/JavaVirtualMachines/1.6.0.jdk/Contents/Classes/classes.jar:/System/Library/Java/JavaVirtualMachines/1.6.0.jdk/Contents/Classes/jsse.jar:/System/Library/Java/JavaVirtualMachines/1.6.0.jdk/Contents/Classes/ui.jar:/usr/lib/jvm/java-6-openjdk-amd64/lib/ext/apple_provider.jar:/usr/lib/jvm/java-6-openjdk-amd64/lib/ext/dnsns.jar:/usr/lib/jvm/java-6-openjdk-amd64/lib/ext/localedata.jar:/usr/lib/jvm/java-6-openjdk-amd64/lib/ext/sunjce_provider.jar:/usr/lib/jvm/java-6-openjdk-amd64/lib/ext/sunpkcs11.jar:/root/try_repose/target/classes:/root/.m2/repository/org/codehaus/groovy/groovy-all/2.1.3/groovy-all-2.1.3.jar:/root/.m2/repository/org/spockframework/spock-core/0.7-groovy-2.0/spock-core-0.7-groovy-2.0.jar:/root/.m2/repository/junit/junit-dep/4.10/junit-dep-4.10.jar:/root/.m2/repository/org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar:/root/.m2/repository/commons-lang/commons-lang/2.6/commons-lang-2.6.jar:/root/.m2/repository/commons-io/commons-io/2.4/commons-io-2.4.jar:/root/.m2/repository/org/linkedin/org.linkedin.util-groovy/1.8.0/org.linkedin.util-groovy-1.8.0.jar:/root/.m2/repository/org/slf4j/jul-to-slf4j/1.5.8/jul-to-slf4j-1.5.8.jar:/root/.m2/repository/org/slf4j/slf4j-api/1.5.8/slf4j-api-1.5.8.jar:/root/.m2/repository/com/fasterxml/jackson/core/jackson-core/2.0.0/jackson-core-2.0.0.jar:/root/.m2/repository/com/fasterxml/jackson/core/jackson-databind/2.0.0/jackson-databind-2.0.0.jar:/root/.m2/repository/com/fasterxml/jackson/core/jackson-annotations/2.0.0/jackson-annotations-2.0.0.jar:/root/.m2/repository/org/apache/ant/ant/1.8.1/ant-1.8.1.jar:/root/.m2/repository/org/apache/ant/ant-launcher/1.8.1/ant-launcher-1.8.1.jar:/root/.m2/repository/org/json/json/20090211/json-20090211.jar:/root/.m2/repository/log4j/log4j/1.2.16/log4j-1.2.16.jar:/root/.m2/repository/org/linkedin/org.linkedin.util-core/1.8.0/org.linkedin.util-core-1.8.0.jar:/root/.m2/repository/org/apache/httpcomponents/httpclient/4.2.5/httpclient-4.2.5.jar:/root/.m2/repository/org/apache/httpcomponents/httpcore/4.2.4/httpcore-4.2.4.jar:/root/.m2/repository/commons-logging/commons-logging/1.1.1/commons-logging-1.1.1.jar:/root/.m2/repository/commons-codec/commons-codec/1.6/commons-codec-1.6.jar:/root/.m2/repository/org/rackspace/gdeproxy/0.16/gdeproxy-0.16.jar" Repose #{method} #{uri} #{headers} '#{body}'`
  end 

  post '/upload' do 
    if params[:file]
      filename = params[:file][:filename]
      file = params[:file][:tempfile]

      FileUtils.rm_rf(Dir.glob("#{Dir.pwd}/imported_configs/*"))

      Zip::File.open(file) do |zipfile|
        zipfile.each do |ex_file|
          # do something with file
          file_name = File.basename(ex_file.name)
          destination = File.join("#{Dir.pwd}/imported_configs", file_name)
          p destination
          p ex_file.name
          p ex_file
          zipfile.extract(ex_file, destination) unless File.exists?(destination)
        end
      end

#      p File.join(Dir.pwd, filename)
#      File.open(File.join(Dir.pwd, filename), 'wb') do |f|
#        f.write file.read
#      end

      'Upload successful'
    else
      'You have to choose a file'
    end
    status 200
    redirect '/'
  end
end

