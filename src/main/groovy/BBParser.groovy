import groovy.json.JsonOutput
import groovy.text.SimpleTemplateEngine
import groovyx.net.http.ContentType
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.Method
import org.ccil.cowan.tagsoup.Parser
import org.yaml.snakeyaml.Yaml

/**
 * Created by ashok.renukappa on 10/17/17.
 */
class BBParser {

    static List<String> BLACK_LIST_DOMAINS = []
    static List<String> BLACKLIST_MOVIE_TITLES = []

    final static List<String> MOVIE_LINK_CSS_CLASSES = [
            'title-link blogpostlist__title-link'
    ]

    final static String MOVIES_INPUT_JSON = 'src/main/angularjs/bindass_bros_movies_input.json'
    final static String MOVIES_INDEX_HTML_TEMPLATE = 'src/main/angularjs/bindass_bros_movies_index.html.template'
    final static String MOVIES_INDEX_OUTPUT_HTML = 'src/main/angularjs/bindass_bros_movies_index.html'

    public static void main(String [] a){

        String configYaml = a[0]
        Yaml parser = new Yaml()
        def configuration = parser.load(this.getClass().getResource(configYaml).text)

        BLACK_LIST_DOMAINS = configuration['blacklist_domains'] as List<String>
        BLACKLIST_MOVIE_TITLES = configuration['blacklist_titles'] as List<String>

        int pageFrom = 1
        int pageTo = 5
        if(configuration['page']){
            pageFrom = (configuration['page']['from']?:1) as Integer
            pageTo = (configuration['page']['to']?:5) as Integer
        }

        long start = System.currentTimeMillis()
        def moviesList = extractMoviesList(pageFrom, pageTo)
        long secs = (System.currentTimeMillis() - start)/1000

        println("Took ${secs} secs to complete whole process...")

        writeHtml(moviesList)

    }

    static def extractMoviesList(int from, int to){

        def baseUrl = 'http://www.bindassbros.com'
        XmlSlurper slurper = new XmlSlurper(new Parser())

        def videoNameToUrlPageMappingMap = [:]

        long start = System.currentTimeMillis()
        for(int i=from ; i<= to; i++){
            String url = "${baseUrl}/blog/page/${i}"
            def html = new URL(url).getText()
            //HTTPBuilder http = new HTTPBuilder(url)
            //http.ignoreSSLIssues()
            //def html = http.get([:])

            def page = slurper.parseText(html)

            def links = page.'**'.findAll { it.name() == 'a'  }

            boolean found = false
            links.each{
                def node ->
                    if(node['@class'] && MOVIE_LINK_CSS_CLASSES.contains(node['@class'])){
                        found=true
                        videoNameToUrlPageMappingMap[node.toString()] = node['@href']*.text()
                    }
            }
            if(!found){
                break
            }
            html
        }

        long secs = (System.currentTimeMillis() - start)/1000

        println("Took ${secs} secs to identify top level video page links...")

        start = System.currentTimeMillis()
        def videoNameToUrls = [:]

        for(Map.Entry videoNameToUrlPageMapping : videoNameToUrlPageMappingMap){
            String videoName = videoNameToUrlPageMapping.key

            videoNameToUrls[(videoName)] = []
            videoNameToUrlPageMapping.value.each {
                String path ->
                    String videoUrlPageUrl = "${baseUrl}/${path}"

                    def html = new URL(videoUrlPageUrl).getText()
                    def page = slurper.parseText(html)

                    def links = page.'**'.findAll { it.name() == 'a'  }

                    links.each {
                        def node ->
                            String href = node['@href']*.text()[0]
                            if(href && ( href.startsWith('http:') || href.startsWith('https:'))){
                                videoNameToUrls[(videoName)] << href
                            }
                    }
            }

        }

        secs = (System.currentTimeMillis() - start)/1000
        println("Took ${secs} secs to identify nested page level video links...")

        List moviesList = []
        for(Map.Entry videoNameToUrl : videoNameToUrls) {

            moviesList << [
                    'title' :  videoNameToUrl.key,
                    'videoUrls' : videoNameToUrl.value
            ]
        }
        moviesList
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
}
