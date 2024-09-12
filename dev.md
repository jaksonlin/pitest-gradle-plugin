# publishing


## setup a simple http server as local maven

```golang
package main

import (
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"

	"github.com/gin-gonic/gin"
)

const repoDir = "./maven-repo"

func main() {
	r := gin.Default()

	// Serve static files
	r.Static("/maven-repo", repoDir)

	// Handle file uploads
	r.PUT("/maven-repo/*path", handleUpload)

	r.Run(":8080")
}

func handleUpload(c *gin.Context) {
	path := c.Param("path")
	fullPath := filepath.Join(repoDir, path)

	// Ensure directory exists
	dir := filepath.Dir(fullPath)
	if err := os.MkdirAll(dir, os.ModePerm); err != nil {
		c.String(http.StatusInternalServerError, fmt.Sprintf("Failed to create directory: %s", err))
		return
	}

	// Create the file
	file, err := os.Create(fullPath)
	if err != nil {
		c.String(http.StatusInternalServerError, fmt.Sprintf("Failed to create file: %s", err))
		return
	}
	defer file.Close()

	// Copy the file content
	_, err = io.Copy(file, c.Request.Body)
	if err != nil {
		c.String(http.StatusInternalServerError, fmt.Sprintf("Failed to write file: %s", err))
		return
	}

	c.String(http.StatusOK, "File uploaded successfully")
}
```

## add build.gradle to publish to local maven

```
publishing {
    publications {
        mavenJava(MavenPublication) {
            from components.java
            groupId = project.group
            artifactId = 'pitest-gradle-plugin'
            version = project.version
        }
    }
    repositories {
        maven {
            name = "TestRepo"
            url = "http://xxx.xx.xx.xx:8080/maven-repo"
        }
    }
}
```

run `gradle publishToMavenLocal` to publish to local maven
run `gradle publish` to publish to the configured remote maven

## build and publish to local maven

`c:\gradle-8.5\bin\gradle publishToMavenLocal`

check the `~/.m2/repository/com/github/jaksonlin/pitest-gradle-plugin/`

## reference

- https://docs.gradle.org/current/userguide/publishing_maven.html
