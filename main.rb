require 'sinatra'
require 'json'
require 'logging'
require 'rbconfig'
require 'open-uri'
require 'zip'

class TryReposeApp < Sinatra::Base
  enable :sessions

  get '/' do
    erb :index
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

    p `java -jar /Users/dimi5963/projects/try_repose/target/try_repose-1.0-SNAPSHOT-jar-with-dependencies.jar #{uri} #{method} #{headers} '#{body}'`
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

