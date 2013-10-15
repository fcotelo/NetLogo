// (C) Uri Wilensky. https://github.com/NetLogo/NetLogo

package org.nlogo.mc;

import org.nlogo.api.CompilerException;
import org.nlogo.api.Observer;
import org.nlogo.api.SimpleJobOwner;
import org.nlogo.nvm.Workspace;
import org.nlogo.nvm.WorkspaceFactory;
import java.awt.image.BufferedImage;

class ImageGenerator {

  ImageGenerator() {
    throw new IllegalStateException("This class cannot be instantiated");
  }

  static Image getAutoGeneratedModelImage(final WorkspaceFactory workspaceFactory) {
    return new Image() {
      @Override
      public BufferedImage getImage() throws ImageException {
        return helper(workspaceFactory);
      }
    };
  }

  private static BufferedImage helper(WorkspaceFactory workspaceFactory)
  throws ImageException {

    BufferedImage image = null;

    try {

      Workspace ws = workspaceFactory.newInstance();
      String         command = "random-seed 0 " + ws.previewCommands();
      SimpleJobOwner owner   = new SimpleJobOwner("ImageGenerator", ws.world().mainRNG, Observer.class);
      ws.runCompiledCommands(owner, ws.compileCommands(command));

      image = ws.exportView();

      ws.dispose();

      return image;

    } catch(InterruptedException e) {
      //headless.dispose method can potentially throw an InterruptedException
      //It doesn't matter if this occurs since we will only reach the dispose line once the image has been
      //generated
      return image;
    } catch(CompilerException compilerException) {
      //Thrown when the code in variable command is invalid NetLogo code and cannot be compiled
      //Should not happen, indicates above code is bad
      throw new ImageException("Could not auto-generate preview image due to invalid auto-generate NetLogo code", compilerException);
    }
  }

}
