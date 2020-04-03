# enjoy_fix

热修复 简单

先将mavenLocal 及fix注释

    mavenLocal()
  classpath 'com.enjoy:fix:1.0' 
  以及app-build.gradle  apply plugin: 'com.enjoy.fix'注释 ，build之后。
  
  点击gradle窗口找到multidex-fix-plugin/Tasks/publishing/publishFixPluginPublicationToMavenLocal运行.
  
  执行成功后 去掉注释。
