// (C) Uri Wilensky. https://github.com/NetLogo/NetLogo

package org.nlogo.headless

import org.scalatest.{ FunSuite, OneInstancePerTest, BeforeAndAfterEach }
import org.nlogo.api.{ Perspective, Version }
import org.nlogo.util.SlowTest

class TestImportExport extends AbstractTestLanguage with FunSuite
with BeforeAndAfterEach with OneInstancePerTest with SlowTest {

  override def beforeEach() {
    init()
    // the default error handler just spits something to stdout or stderr or somewhere.
    // we want to fail hard. - ST 7/21/10
    workspace.importerErrorHandler =
      new org.nlogo.agent.ImporterJ.ErrorHandler() {
        def showError(title: String, errorDetails: String, fatalError: Boolean): Boolean =
          sys.error(title + " / " + errorDetails + " / " + fatalError)
      }
  }

  override def afterEach() { workspace.dispose() }

  def getUniqueFilename() = {
    new java.io.File("tmp").mkdir()
    new java.io.File("tmp/TestImportExport").mkdir()
    val result = "tmp/TestImportExport/" + System.nanoTime + ".csv"
    delete(result)
    result
  }

  def delete(path: String): Boolean =
    new java.io.File(path).delete()

  def roundTripHelper(setup: String,
                      model: String = "",
                      worldSize: Int = 0) {
    val filename = getUniqueFilename()

    // get ready
    workspace.initForTesting(worldSize, model)
    testCommand("random-seed 378234"); // just some number I made up

    // run the setup commands, run export-world, and slurp the resulting export into a string
    testCommand(setup)
    testCommand("export-world \"" + filename + "\"")
    val export1 = org.nlogo.api.FileIO.file2String(filename)

    // alter the state of the random number generator
    testCommand("repeat 500 [ __ignore random 100 ]")

    // reimport the export we just created
    testCommand("ca")
    testReporter("count turtles", "0")
    testCommand("import-world \"" + filename + "\"")
    assert(delete(filename))

    testCommand("export-world \"" + filename + "\"")

    // new slurp the second export into a string
    val export2 = org.nlogo.api.FileIO.file2String(filename)
    assert(delete(filename))

    // the two strings exports be equal except for the date
    expect(dropLines(export1, 3))(
      dropLines(export2, 3))
  }

  def dropLines(s: String, n: Int): String =
    io.Source.fromString(s).getLines.drop(n).mkString("\n")

  /// tests that use roundTripHelper

  test("testRoundTripEmpty") {
    roundTripHelper("")
  }

  test("testRoundTripTicks1") {
    roundTripHelper("reset-ticks")
  }

  test("testRoundTripTicks2") {
    roundTripHelper("reset-ticks tick")
  }

  test("testRoundTripTicks3") {
    roundTripHelper("reset-ticks tick clear-ticks")
  }

  test("testRoundTripSimple") {
    roundTripHelper("crt 30 [ set heading who * 90 fd who ]",
      worldSize = 5)
  }

  test("RoundTripComplexNewFormat") {
    roundTripHelper("setup true", COMPLEX_SOURCE, worldSize = 3)
  }

  test("testRoundTripSpecialCharacter") {
    // 8211 and 8212 are some arbitrary unicode values. we use numbers since we don't want non-ASCII
    // characters in the source files -- ST 2/14/07
    roundTripHelper("ask one-of patches [ set plabel \""
      + 8211.toChar
      + 8212.toChar
      + "\" ]")
  }

  test("testRoundTripAllTurtlesAllPatches1") {
    roundTripHelper("set x turtles set y patches",
      "globals [x y]")
  }

  test("testAgentsStoredInAgentVariables") {
    roundTripHelper("cro 4 [ create-links-with other turtles ]\n" +
      "ask turtle 0 [ set label one-of other turtles ]\n" +
      "ask turtle 1 [ set label one-of patches ]\n" +
      "ask turtle 2 [ set label sort turtles ]\n" +
      "ask turtle 3 [ set label sort patches ]")
  }

  test("testBreededTurtlesStoredInAgentVariables") {
    roundTripHelper("create-ordered-mice 2 [ create-links-with other mice ]\n" +
      "ask turtle 0 [ set label one-of other mice ]\n" +
      "ask turtle 1 [ set label sort mice ]",
      "breed [mice mouse]")
  }

  // ticket #934
  test("testLinksStoredInAgentVariables") {
    roundTripHelper("cro 2 [ create-links-with other turtles ]\n" +
      "ask turtle 0 [ set label one-of links ]\n" +
      "ask turtle 1 [ set label sort links]\n")
  }

  // more ticket #934
  test("testBreededLinksStoredInAgentVariables") {
    roundTripHelper("cro 2 [ create-shipments-to other turtles ]\n" +
      "ask turtle 0 [ set label one-of shipments ]\n" +
      "ask turtle 1 [ set label sort shipments]\n",
      "directed-link-breed [shipments shipment]")
  }

  test("testRoundTripAllTurtlesAllPatches2") {
    // the bug we're testing for is elusive and may only appear if we actually change the world size
    // around - ST 12/21/04
    val filename = getUniqueFilename()
    workspace.initForTesting(5, "globals [x y]")
    testCommand("crt 10 set x turtles set y patches")
    testCommand("export-world \"" + filename + "\"")
    workspace.initForTesting(6, "globals [x y]")
    testCommand("import-world \"" + filename + "\"")
    assert(delete(filename))
    testReporter("x = turtles", "true")
    testReporter("y = patches", "true")
  }

  test("testImportDrawing") {
    val filename = getUniqueFilename()
    workspace.initForTesting(10)
    testCommand("random-seed 2843")
    testCommand("crt 10 [ pd  set pen-size random 5 ]")
    testCommand("ask turtles [ fd random 5 ]")
    val realColors = workspace.renderer.trailDrawer.colors
    testCommand("export-world \"" + filename + "\"")
    testCommand("ca")
    testCommand("import-world \"" + filename + "\"")
    val importedColors = workspace.renderer.trailDrawer.colors

    expect(realColors.size)(importedColors.size)

    // right now we can't test the pixels because they are not pixel for pixel the same, what we
    // import is exactly as was, however, when we copy it for one image to the drawing image the
    // colors can change ever so slightly. (we copy images because of an apple bug, see the
    // TrailDrawer for details.  ev 3/1/06
  }

  test("testExportOutputArea") {
    val filename = getUniqueFilename()
    workspace.initForTesting(10)
    testCommand("ca")
    testCommand("output-print \"This is a test of output areas.\"");
    testCommand("export-world \"" + filename + "\"")
    val expected = Checksummer.calculateWorldChecksum(workspace)
    testCommand("import-world \"" + filename + "\"")
    val actual = Checksummer.calculateWorldChecksum(workspace)
    expect(expected)(actual)
  }

  test("testExportLinks") {
    val filename = getUniqueFilename()
    workspace.initForTesting(10, HeadlessWorkspace.TestDeclarations)
    testCommand("ca")
    testCommand("create-ordered-nodes 2 [ fd 2 ]")
    testCommand("ask node 0 [ create-link-to node 1 [ tie ] ]")
    testCommand("export-world \"" + filename + "\"")
    val expected = Checksummer.calculateWorldChecksum(workspace)
    testCommand("import-world \"" + filename + "\"")
    val actual = Checksummer.calculateWorldChecksum(workspace)
    expect(expected)(actual)
    testReporter("count links", "1")
    testReporter("count nodes", "2")
    testReporter("[heading] of node 1", "180")
    testCommand("ask node 0 [ rt 180 ] ")
    testReporter("[heading] of node 1", "0")
  }

  test("testImportInvalidSize") {
    workspace.initForTesting(10)
    workspace.importerErrorHandler =
      new org.nlogo.agent.ImporterJ.ErrorHandler() {
        def showError(title: String, errorDetails: String, fatalError: Boolean): Boolean =
          {
            assert(!fatalError)
            expect("Error Importing Drawing")(title)
            expect("Invalid data length, the drawing will not be imported")(
              errorDetails)
            true
          }
      }
    testCommand("import-world \"test/import/invalid-drawing.csv\"")
  }

  test("testImportDrawingIncompleteData") {
    workspace.initForTesting(10)
    workspace.importerErrorHandler =
      new org.nlogo.agent.ImporterJ.ErrorHandler() {
        def showError(title: String, errorDetails: String, fatalError: Boolean): Boolean = {
          assert(!fatalError)
          expect("Error Importing Drawing")(title)
          expect("Invalid data length, the drawing will not be imported")(
            errorDetails)
          true
        }
      }
    testCommand("import-world \"test/import/short-drawing.csv\"")
  }

  test("testImportSubject") {
    val filename = getUniqueFilename()
    workspace.initForTesting(10)
    testCommand("export-world \"" + filename + "\"")
    testCommand("import-world \"" + filename + "\"")
    testReporter("subject", "nobody")
    expect(workspace.world.observer().perspective())(Perspective.Observe)
    testCommand("crt 1")
    testCommand("watch turtle 0")
    testCommand("export-world \"" + filename + "\"")
    testCommand("ca")
    testCommand("import-world \"" + filename + "\"")
    testReporter("[who] of subject", "0")
    expect(workspace.world.observer().perspective())(Perspective.Watch)
    testCommand("crt 1")
    testCommand("follow turtle 1")
    testCommand("export-world \"" + filename + "\"")
    testCommand("ca")
    testCommand("import-world \"" + filename + "\"")
    testReporter("[who] of subject", "1")
    expect(workspace.world.observer().perspective())(Perspective.Follow)
    testCommand("ride turtle 1")
    testCommand("export-world \"" + filename + "\"")
    testCommand("ca")
    testCommand("import-world \"" + filename + "\"")
    testReporter("[who] of subject", "1")
    expect(workspace.world.observer().perspective())(Perspective.Ride)
  }

  test("testNonExistentPlot") {
    workspace.initForTesting(10)
    workspace.importerErrorHandler =
      new org.nlogo.agent.ImporterJ.ErrorHandler() {
        def showError(title: String, errorDetails: String, fatalError: Boolean) = {
          assert(!fatalError)
          expect("Error Importing Plots")(title)
          expect("The plot \"plot 2\" does not exist.")(
            errorDetails)
          true
        }
      }
    testCommand("import-world \"test/import/plot-simple.csv\"")
  }

  test("testNonExistentPen") {
    workspace.open("test/import/plot-simple.nlogo")
    workspace.importerErrorHandler =
      new org.nlogo.agent.ImporterJ.ErrorHandler() {
        def showError(title: String, errorDetails: String,
                      fatalError: Boolean) =
          {
            assert(!fatalError)
            expect("Error Importing Plots")(title)
            expect("The pen \"default 1\" does not exist.")(errorDetails)
            true
          }}
    testCommand("import-world \"plot-simple.csv\"")
  }

  test("testCustomPenColor") {
    val filename = getUniqueFilename()
    workspace.open("test/import/plot-custom-color.nlogo")
    testCommand("export-world \"../../" + filename + "\"")
    val export1 = org.nlogo.api.FileIO.file2String(filename)
    testCommand("ca import-world \"../../" + filename + "\"")
    testCommand("export-world \"../../" + filename + "\"")
    val export2 = org.nlogo.api.FileIO.file2String(filename)
    expect(dropLines(export1, 3))(
      dropLines(export2, 3))
  }

  test("testImportingTurtlesDying") {
    val filename = getUniqueFilename()
    workspace.initForTesting(10)
    testCommand("crt 10")
    testCommand("ask turtle 9 [ die ]")
    testCommand("ask turtle 5 [ die ]")
    testCommand("export-world \"" + filename + "\"")
    testCommand("ca")
    testCommand("import-world \"" + filename + "\"")
    testCommand("ask turtle 4 [ die ]")
    testCommand("crt 2")
    testReporter("sort [who] of turtles", "[0 1 2 3 6 7 8 10 11]")
  }

  /// other tests (that don't use roundTripHelper)

  test("testTrailingCommas") {
    workspace.initForTesting(35, new org.nlogo.api.LocalFile(
      "test/import/trailing-commas.nlogo").readFile())
    testCommand("import-world \"test/import/trailing-commas.csv\"")
  }

  test("ImportWrongOrder") {
    workspace.initForTesting(10)
    workspace.importerErrorHandler =
      new org.nlogo.agent.ImporterJ.ErrorHandler() {
        def showError(title: String, errorDetails: String, fatalError: Boolean) = {
            assert(fatalError)
            expect("Fatal Error- Incorrect Structure For Import File")(title)
            expect("The agents are in the wrong order in the import file. " +
              "The global variables should be first, followed by the turtles, " +
              "followed by the patches.  Found TURTLES but needed " +
              "GLOBALS\n\nThe import will now abort.")(errorDetails)
            true
          }}
    testCommand("import-world \"test/import/wrong-order.csv\"")
  }

  test("ImportSentinelName") {
    workspace.initForTesting(10)
    testCommand("import-world \"test/import/TURTLES.csv\"")
  }

  test("ExtraFieldValue") {
    workspace.initForTesting(35, new org.nlogo.api.LocalFile(
      "test/import/trailing-commas.nlogo").readFile())
    val errorNumber = Array(0)
    workspace.importerErrorHandler =
      new org.nlogo.agent.ImporterJ.ErrorHandler() {
        def showError(title: String, errorDetails: String, fatalError: Boolean) = {
          assert(!fatalError)
          expect("Warning: Too Many Values For Agent")(title)
          errorNumber(0) match {
            case 0 =>
              expect("Error Importing at Line 7: There are a total of "
                + "10 Global variables declared in this model "
                + "(including built-in variables).  The import-world "
                + "file has at least one agent in the GLOBALS section "
                + "with more than this number of values.\n\n"
                + "Action to be Taken: All the extra values will "
                + "be ignored for this section.")(
                errorDetails)
            case 1 =>
              expect("Error Importing at Line 54: There are a total of "
                + "5 Patch variables declared in this model "
                + "(including built-in variables).  The import-world "
                + "file has at least one agent in the PATCHES section "
                + "with more than this number of values.\n\n"
                + "Action to be Taken: All the extra values will "
                + "be ignored for this section.")(
                errorDetails)
            case _ =>
              fail()
          }
          errorNumber(0) += 1
          true
        }
      }
    testCommand("import-world \"test/import/extra-values.csv\"")
    expect(2)(errorNumber(0))
  }

  // this is a focused test with a small number of turtles
  // designed to catch one particular known bug
  test("testReproducibilityOfWhoNumberAssignment1") {
    val filename = getUniqueFilename()
    workspace.initForTesting(0)
    testCommand("crt 4")
    testCommand("ask turtle 0 [ die ]")
    testCommand("ask turtle 2 [ die ]")
    testCommand("export-world \"" + filename + "\"")
    testCommand("crt 1")
    testReporter("sort [who] of turtles", "[1 3 4]")
    testCommand("import-world \"" + filename + "\"")
    testCommand("crt 1")
    testReporter("sort [who] of turtles", "[1 3 4]")
    assert(delete(filename))
  }

  // this is a focused test with a small number of turtles
  // designed to catch one particular known bug
  test("testReproducibilityOfWhoNumberAssignment2") {
    val filename = getUniqueFilename()
    workspace.initForTesting(0)
    testCommand("crt 4")
    testCommand("ask turtle 2 [ die ]")
    testCommand("ask turtle 0 [ die ]")
    testCommand("export-world \"" + filename + "\"")
    testCommand("crt 1")
    testReporter("sort [who] of turtles", "[1 3 4]")
    testCommand("import-world \"" + filename + "\"")
    testCommand("crt 1")
    testReporter("sort [who] of turtles", "[1 3 4]")
    assert(delete(filename))
  }

  // this is a less focused test with lots of turtles
  // that might hopefully catch new bugs
  test("testReproducibilityOfWhoNumberAssignment3") {
    val filename = getUniqueFilename()
    workspace.initForTesting(0)
    testCommand("crt 500")
    testCommand("ask turtles [ if random 2 = 0 [ die ] ]")
    testCommand("export-world \"" + filename + "\"");
    testCommand("crt 100")
    val actualResult = workspace.report("sum [who] of turtles")
    testCommand("import-world \"" + filename + "\"")
    testCommand("crt 100")
    val newResult = workspace.report("sum [who] of turtles")
    expect(actualResult)(newResult)
    assert(delete(filename))
  }

  test("utf 8 string") {
    val x = new String("A" + "\u00ea" + "\u00f1" + "\u00fc" + "C")
    roundTripHelper(setup="set t \"" + x + "\"", model="globals [t]")
  }

  ///

  val COMPLEX_SOURCE =
    "globals [ g-string-test g-list-test nobody-var one-turtle one-patch " +
      "          empty-patch-agentset empty-turtle-agentset cats-breed all-turtles all-patches ]" +
      "turtles-own [ t-foo t-bar ]" +
      "patches-own [ p-foo p-fish-breed ]" +
      "breed [ fish ] " +
      "breed [ cats ]" +
      "fish-own [ scales? fins ]" +
      "cats-own [ fur tabby? ]" +
      "to setup [ add-breed-to-list? ]" +
      "  ca" +
      "  crt 4" +
      "  [" +
      "    set t-foo \"just, a, string\\\\ with, funny stuff\\t\\\"in it\"" +
      "    set t-bar list [] [ true false \"hithere, how's it going?\" 4 10.2 ]" +
      "  ]" +
      "  ask turtle 0" +
      "  [" +
      "    setxy 7 7" +
      "    set breed fish" +
      "    set scales? true" +
      "    set fins 20.5" +
      "  ]" +
      "  ask turtle 1" +
      "  [" +
      "    setxy 14 14" +
      "    set breed cats" +
      "    set color blue" +
      "    set fur \"thick,and,well groomed\"" +
      "    set tabby? false" +
      "    fd 1" +
      "  ]" +
      "  ask turtle 2" +
      "  [" +
      "    setxy 6 5" +
      "    set label \"15,4,5,\" " +
      "  ]" +
      "  ask turtle 3" +
      "  [" +
      "    setxy 14 11" +
      "  ]" +
      "  set g-string-test \"8\\\\the\\tchili\\nand it\\rwas \\\"good\\\",,,,, by golly!! {patches [2 2] [-2 1] [-1 1] [2 0] [0 -2]},[{patches [-2 2] [2 2] [0 0] [2 -2] [1 -3]} {turtles 1 3}]\"" +
      "  set g-list-test lput g-string-test lput (turtle-set turtle 0 turtle 2 turtle 3) lput (patch-set patch 3 2 patch -3 1 patch -1 0 patch 2 0 patch -3 -3) fput (turtle 1) list [] (patch 0 0)" +
      "  " +
      "  ;; we don't want to add the fish breed if we are importing the\n " +
      "  ;; old format since it couldn't support breeds in lists.\n" +
      "  if add-breed-to-list?" +
      "  [ set g-list-test lput fish g-list-test ]" +
      "  " +
      "  set nobody-var nobody" +
      "  set one-patch (patch 0 0)" +
      "  set one-turtle (turtle 0)" +
      "  set empty-patch-agentset patches with [ pcolor = 139.2356 ]" +
      "  set empty-turtle-agentset turtles with [ who > 100000 ]" +
      "  set cats-breed cats" +
      "  set all-turtles turtles" +
      "  set all-patches patches" +
      "  ask patch -3 3" +
      "  [" +
      "    set plabel-color yellow" +
      "    set plabel 25" +
      "  ]" +
      "  ask patches with [ any? turtles-here ]" +
      "  [" +
      "    set pcolor yellow" +
      "    set p-foo nobody-var" +
      "    set p-fish-breed fish" +
      "  ]" +
      "end"
}