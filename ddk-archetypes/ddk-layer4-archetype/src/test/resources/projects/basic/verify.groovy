// 生成的项目要显式声明 Lombok 注解处理器：JDK 23 起 javac 不再运行 classpath 上的处理器
// basedir 是 IT 目录，生成的项目在 project/<artifactId> 下
def poms = new File(basedir, 'project').listFiles().collect { new File(it, 'pom.xml') }.findAll { it.isFile() }
assert poms.size() == 1 : "expected one generated project under ${basedir}/project, found ${poms}"
def pom = poms[0]
def project = new groovy.xml.XmlSlurper().parse(pom)
def compiler = project.build.plugins.plugin.find { it.artifactId.text() == 'maven-compiler-plugin' }
def lombok = compiler.configuration.annotationProcessorPaths.path.find { it.artifactId.text() == 'lombok' }
assert lombok.size() == 1 : 'maven-compiler-plugin does not list lombok in annotationProcessorPaths'
assert lombok.version.text() == '${lombok.version}' : "lombok processor version is ${lombok.version.text()}, expected the Spring Boot managed property"
return true
