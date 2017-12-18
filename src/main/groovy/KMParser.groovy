/**
 * Created by ashok.renukappa on 5/27/17.
 */


import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.text.SimpleTemplateEngine
import groovy.util.slurpersupport.NodeChild
import groovy.util.slurpersupport.Node
import groovyx.net.http.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.yaml.snakeyaml.Yaml
import sun.org.mozilla.javascript.internal.NativeObject

import javax.script.Bindings
import javax.script.ScriptContext
import javax.script.ScriptEngine
import javax.script.ScriptEngineManager


class KMParser {

    static String SITE_FILES_MAP_VAR_NAME = 'SiteFilesMap'
    static String DBSITE_META_DATA_VAR_NAME = 'DBSiteMetaData'

    static List<String> BLACK_LIST_DOMAINS = []
    static List<String> BLACKLIST_MOVIE_TITLES = []

    final static String MOVIES_INPUT_JSON = 'src/main/angularjs/kannada_movies_biz_movies_input.json'
    final static String MOVIES_INDEX_HTML_TEMPLATE = 'src/main/angularjs/kannada_movies_biz_movies_index.html.template'
    final static String MOVIES_INDEX_OUTPUT_HTML = 'src/main/angularjs/kannada_movies_biz_movies_index.html'

    public static void main(String [] a){

        String configYaml = a[0]
        Yaml parser = new Yaml()
        def configuration = parser.load(this.getClass().getResource(configYaml).text)

        BLACK_LIST_DOMAINS = configuration['blacklist_domains'] as List<String>
        BLACKLIST_MOVIE_TITLES = configuration['blacklist_titles'] as List<String>

        long start = System.currentTimeMillis()
        def moviesList = extractMoviesList()
        long secs = (System.currentTimeMillis() - start)/1000

        println("Took ${secs} secs to identify links...")

        writeHtml(moviesList)
    }

    static void writeHtml(def moviesList){
        def json = JsonOutput.toJson(moviesList)

        new File(MOVIES_INPUT_JSON).text = JsonOutput.prettyPrint(json)

        def templateFile = new File(MOVIES_INDEX_HTML_TEMPLATE)

        def binding = ["moviesData": JsonOutput.prettyPrint(json) ]
        def engine = new SimpleTemplateEngine()
        def template = engine.createTemplate(templateFile.text).make(binding)

        def htmlFile = new File(MOVIES_INDEX_OUTPUT_HTML)
        htmlFile.text = template.toString()
        println ("Open ${htmlFile.absolutePath}")
    }


    static List<Node> scriptNodes(Node headHtml){
        List<Node> scriptNodes = []
        headHtml.childNodes().each {
            Node nodeChild ->
                if ( nodeChild.name() == 'SCRIPT'){
                    scriptNodes.add( nodeChild )
                }
        }
        scriptNodes
    }

    static Map extractIdToTitleMap(def dbSiteMetaData ){
        def idToTitleMap = [:]
        dbSiteMetaData['pagesStructureInformation']['pagesData'].each {
            def pageData ->

                String pageId = pageData.value['id']
                String pageTitle = pageData.value['title']

                if(pageId.startsWith('id')){
                    boolean blackListed = BLACKLIST_MOVIE_TITLES.find{
                        pageTitle.toLowerCase().contains(it.toLowerCase())
                    }
                    if(!blackListed){
                        //Keys (ids) have 'page-' prefix in siteFilesMap
                        idToTitleMap[ ("page-${pageId}") ] = pageTitle
                    }
                }
        }
        idToTitleMap
    }

    static Map extractIdToJsUrlMap(def siteFilesMap, def idToTitleMap){
        def idToJsUrlMap = [:]
        siteFilesMap.each {
            if(idToTitleMap.keySet().contains(it.key)){
                idToJsUrlMap[it.key] = it.value
            }
        }

        idToJsUrlMap
    }

    static Map extractIdToIframeContentMap(def idToJsUrlMap){
        def idToIframeContentMap = [:]
        idToJsUrlMap.each {
            String pageId, String jsUrl ->

                def tmpFile = File.createTempFile("temp",".js")
                def file = tmpFile.newOutputStream()
                file << new URL(jsUrl).openStream()
                file.close()
                def jsContent = tmpFile.text

                if(jsContent?.trim()?.startsWith('PagesStructures')){
                    String idInPageId = pageId.substring('page-'.length())
                    String varName = "PagesStructures['${idInPageId}'] = "
                    def json = new JsonSlurper().parseText(jsContent.substring(varName.length()))
                    String embedHtml = json['structures'][0]['childElements'][0]['childElements'][0]['elementProperties']['embed_html_app1html']
                    if(embedHtml){
                        embedHtml = embedHtml.replaceAll('\\\\','')
                        idToIframeContentMap[pageId] = embedHtml
                    }
                }
                tmpFile.delete()
        }
        idToIframeContentMap
    }

    static List extractMoviesList(){
        def http = new HTTPBuilder("http://kannadamovies.biz")

        def html = http.get([:])

        Node headHtml = (Node)((NodeChild)html).childNodes()[0]
        List<Node> scriptNodes = scriptNodes(headHtml)

        def siteFilesMap = [:]
        def dbSiteMetaData = [:]
        scriptNodes.each {
            Node scriptNode ->
                scriptNode.children().each {
                    if(it.toString().contains(SITE_FILES_MAP_VAR_NAME)){
                        siteFilesMap = new JsonSlurper().parseText(extractJSVarValue(SITE_FILES_MAP_VAR_NAME,it.toString()))
                    }
                    if(it.toString().contains(DBSITE_META_DATA_VAR_NAME)){
                        dbSiteMetaData = new JsonSlurper().parseText(extractJSVarValue(DBSITE_META_DATA_VAR_NAME,it.toString()))
                    }
                }
        }

        def idToTitleMap = extractIdToTitleMap(dbSiteMetaData)
        def idToJsUrlMap = extractIdToJsUrlMap(siteFilesMap, idToTitleMap)
        def idToIframeContentMap = extractIdToIframeContentMap(idToJsUrlMap)

        def moviesList = []

        idToTitleMap.each {
            String id, String title ->
                String iframeHtml = idToIframeContentMap[id] as String
                if(iframeHtml){
                    boolean blackListed = BLACKLIST_MOVIE_TITLES.find{
                        title.toLowerCase().contains(it.toLowerCase())
                    }
                    if(!blackListed){
                        def urls = extractUrls(iframeHtml)
                        if(urls){
                            moviesList.add(new MovieInfo(id , title, idToJsUrlMap[id] as String, idToIframeContentMap[id] as String, urls))
                        }
                    }
                }
        }

        moviesList
    }

    static List<String> extractUrls(String html){
        Document doc = Jsoup.parse(html)
        List<String> urls = []
        doc.select("a").iterator().each {
            String link = it.attr("href")
            if(!isBlackListed(link)){
                urls.add( link )
            }
        }

        doc.select("iframe").iterator().each {
            String link = it.attr("src")
            if(!isBlackListed(link)){
                if(!link.startsWith('http:') && !link.startsWith('https:')){
                    link = "http:${link}"
                }
                urls.add( link )
            }
        }
        urls
    }

    static boolean isBlackListed(String url){
        BLACK_LIST_DOMAINS.find{ String blackListed -> url.toLowerCase().contains(blackListed) }
    }

    static class MovieInfo{
        String id
        String title
        String jsUrl
        String iframeContent
        List videoUrls

        MovieInfo(String id, String title, String jsUrl, String iframeContent, List videoUrls) {
            this.id = id
            this.title = title
            this.jsUrl = jsUrl
            this.iframeContent = iframeContent
            this.videoUrls = videoUrls
        }
    }
    static String extractJSVarValue(String varName, String jsCode){
        ScriptEngineManager mgr = new ScriptEngineManager();
        ScriptEngine se = mgr.getEngineByName("JavaScript");

        def varValue = null
        try {
            se.eval(jsCode);
            Bindings bindings = se.getBindings(ScriptContext.ENGINE_SCOPE);
            //System.out.println(bindings.keySet());

            varValue = bindings.get(varName)
        }catch (ScriptException e) {
            //e.printStackTrace();
        }catch ( Throwable t){
            if(t.message.contains('"Viewer" is not defined')){
                jsCode = jsCode.replaceAll("Viewer.initialize\\(\\);",'')
                varValue = extractJSVarValue(varName, jsCode)
            }else{
                println("Got runtime exception while parsing JS continuing...  " + t.getMessage())
            }
        }

        convertToString(varValue)
    }

    static String convertToString(def varValue){
        if (varValue instanceof String){
            return varValue
        }
        if(varValue instanceof NativeObject){
            if(varValue.size() > 0){//It's a list
                String entries = null
                varValue.each {
                    if(entries){
                        entries +=" , " + convertToString(it)
                    }else{
                        entries = convertToString(it)
                    }
                }
                entries = "{ ${entries} }"
                return entries
            }

        }
        if(varValue instanceof Map.Entry){
            if(varValue.value instanceof String){
                return """ "${varValue.key}" : "${varValue.value}" """
            }else{
                return """ "${varValue.key}" : ${convertToString(varValue.value)} """
            }

        }
    }
}
