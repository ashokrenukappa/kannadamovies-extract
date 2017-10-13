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
Support for running Gradle using Java 7 has been deprecated and is scheduled to be removed in Gradle 5.0. Please see https://docs.gradle.org/4.2/userguide/java_plugin.html#sec:java_cross_compilation for more details.

> Task :createHtml
Took 140 secs to identify links...
Open ************************src/main/angularjs/movies_index.html


BUILD SUCCESSFUL in 2m 25s
3 actionable tasks: 3 executed
```

### View Movies links

Open `********************/src/main/angularjs/movies_index.html` in a web browser.