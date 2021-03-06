package org.ensime.test.util

import akka.actor.ActorSystem
import akka.event.slf4j.SLF4JLogging
import akka.testkit.TestProbe
import java.io.File
import org.slf4j.LoggerFactory

import scala.tools.nsc.Settings
import scala.tools.nsc.reporters.StoreReporter
import scala.reflect.internal.util.BatchSourceFile
import org.ensime.server._
import org.ensime.indexer._
import org.ensime.config._
import org.ensime.test.TestUtil
import org.scalatest.exceptions.TestFailedException
import scala.tools.nsc.interactive.Global

import pimpathon.file._
import TestUtil._

object Helpers {

  def withPresCompiler(action: (File, RichCompilerControl) => Any) =
    withTempDirectory { tmp =>
      require(tmp.isDirectory)
      implicit val actorSystem = ActorSystem.create()

      val presCompLog = LoggerFactory.getLogger(classOf[Global])
      val settings = new Settings(presCompLog.error)
      settings.embeddedDefaults[RichCompilerControl]
      settings.YpresentationDebug.value = presCompLog.isTraceEnabled
      settings.YpresentationVerbose.value = presCompLog.isDebugEnabled
      settings.verbose.value = presCompLog.isDebugEnabled
      //settings.usejavacp.value = true
      settings.bootclasspath.append(TestUtil.scalaLib.getAbsolutePath)

      val reporter = new StoreReporter()
      val indexer = TestProbe()
      val parent = TestProbe()

      val config = basicConfig(tmp)
      val resolver = new SourceResolver(config)
      val search = new SearchService(config, resolver)

      val cc = new RichPresentationCompiler(
        config, settings, reporter, parent.ref, indexer.ref, search
      )
      try {
        action(tmp, cc)
      } finally {
        cc.askShutdown()
        actorSystem.shutdown()
      }
    }

  // TODO: needs to be in the right place, need to get tmp dir
  def srcFile(base: File, name: String, content: String) =
    new BatchSourceFile((base / mainSourcePath / name).getPath, content)

  def contents(lines: String*) = lines.mkString("\n")

  def expectFailure(msgLines: String*)(action: () => Unit) {
    try {
      action()
      throw new IllegalStateException("Expected failure! Should not have succeeded!")
    } catch {
      case e: TestFailedException =>
      case e: Throwable => throw e
    }
  }

}
