# Extract Movies List from http://kannadamovies.biz , http://www.bindassbros.com

### Configuration for kannadamovies.biz

```bash
src/main/resources/config_km.yaml
```

### Configuration for bindassbros.com

```bash
src/main/resources/config_bb.yaml
```

### Generate html from kannadamovies.biz
```bash
./gradlew kannadeMoviesHtml
```

### Generate html from bindassbros.com
```bash
./gradlew bindassBrosHtml
```


### Generate html for all sites
```bash
./gradlew createHtml
```

### View Movies links

Open `********************/src/main/angularjs/bindass_bros_movies_index.html` in a web browser.