package ni.vsbuild.nipm

import ni.vsbuild.BuildInformation
import ni.vsbuild.stages.*

class Pipeline implements Serializable {

   def script
   def prebuildStages = []
   def buildStages = []
   BuildInformation buildInformation

   static builder(script, BuildInformation buildInformation) {
      return new Builder(script, buildInformation)
   }

   static class Builder implements Serializable {

      def script
      def prebuildStages = []
      def buildStages = []
      BuildInformation buildInformation

      Builder(def script, BuildInformation buildInformation) {
         this.script = script
         this.buildInformation = buildInformation
      }

      def withInitialCleanStage() {
         prebuildStages << new InitialClean(script)
      }

      def withCheckoutStage() {
         prebuildStages << new Checkout(script)
      }
    
      def withSetupStage() {
         buildStages << new Setup(script)
      }
      
      def withUnitTestStage() {
         buildStages << new UnitTest(script)
      }
      
      def withCodegenStage() {
         buildStages << new Codegen(script)
      }
      
      def withBuildStage() {
         buildStages << new Build(script)
      }
      
      def withArchiveStage() {
         buildStages << new Archive(script)
      }
      
      def withPackageStage() {
         buildStages << new PackageBuild(script)
      }
      
      def withPublishStage() {
         buildStages << new Publish(script)
      }
      
      def withCleanupStage() {
         buildStages << new Cleanup(script)
      }
      
      def buildPipeline() {
         withInitialCleanStage()
         withCheckoutStage()
         
         withSetupStage()
         
         if(buildInformation.officiallySupported) {
            withUnitTestStage()
         }
         
         withCodegenStage()
         withBuildStage()
         
         if(!buildInformation.officiallySupported) {
            withArchiveStage()
         }
         
         withPackageStage()
         
         if(script.env['BRANCH_NAME'] == 'master') {
            withPublishStage()
         }
         
         withCleanupStage()
         
         return new Pipeline(this)
      }
   }

   private Pipeline(Builder builder) {
      this.script = builder.script
      this.prebuildStages = builder.prebuildStages
      this.buildStages = builder.buildStages
      this.buildInformation = builder.buildInformation
   }

   void execute() {

      // build dependencies before starting this pipeline
      script.buildDependencies(buildInformation)
      
      script.node(buildInformation.nodeLabel) {
         
         def executor
         
         executeStages(prebuildStages, executor)
         
         // This load must happen after the checkout stage, but before any
         // stage that requires the build steps to be loaded
         executor = buildInformation.createExecutor(script)
         
         executeStages(buildStages, executor)
      }
   }
   
   void executeParallel() {
      def builders = [:]
      
      for (String version : buildInformation.lvVersions) {
         builders[version] = {
            script.buildDependencies(buildInformation)
            
            script.node(buildInformation.nodeLabel) {
               def executor
               
               executeStages(prebuildStages, executor)
               
               executor = buildInformation.createExecutor(script, version)
               
               executeStages(buildStages, executor)
            }
         }
      }
      
      script.parallel builders
   }
   
   private void executeStages(stages, executor) {
      for (Stage stage : stages) {
         try {
            stage.execute(executor)
         } catch (err) {
            script.currentBuild.result = "FAILURE"
            script.error "Build failed: ${err.getMessage()}"
         }
      }
   }
}
