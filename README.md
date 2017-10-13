# Extract Movies List from http://kannadamovies.biz

### Configuration

```bash
src/main/resources/config.yaml
```

### Run
```bash
./gradlew createHtml
```

### Sample output
```bash
Starting a Gradle Daemon, 1 stopped Daemon could not be reused, use --status for details
:compileJava UP-TO-DATE
:compileGroovy
:processResources
:classes
:createHtml
Took 137 secs to identify links...
Open ********************/src/main/angularjs/movies_index.html

BUILD SUCCESSFUL

Total time: 2 mins 25.05 secs
```

### View Movies links

Open `********************/src/main/angularjs/movies_index.html` in a web browser.