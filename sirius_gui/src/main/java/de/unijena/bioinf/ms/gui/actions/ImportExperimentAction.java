package de.unijena.bioinf.ms.gui.actions;
/**
 * Created by Markus Fleischauer (markus.fleischauer@gmail.com)
 * as part of the sirius_frontend
 * 29.01.17.
 */

import de.unijena.bioinf.babelms.projectspace.GuiProjectSpace;
import de.unijena.bioinf.ms.gui.configs.Icons;
import de.unijena.bioinf.babelms.load.LoadController;
import de.unijena.bioinf.ms.gui.sirius.ExperimentResultBean;

import javax.swing.*;
import java.awt.event.ActionEvent;

import static de.unijena.bioinf.ms.gui.mainframe.MainFrame.MF;

/**
 * @author Markus Fleischauer (markus.fleischauer@gmail.com)
 */
public class ImportExperimentAction extends AbstractAction {
    public ImportExperimentAction() {
        super("Import");
        putValue(Action.LARGE_ICON_KEY, Icons.DOC_32);
        putValue(Action.SMALL_ICON, Icons.ADD_DOC_16);
        putValue(Action.SHORT_DESCRIPTION, "Import measurements of a single compound");
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        LoadController lc = new LoadController(MF);
        lc.showDialog();
        ExperimentResultBean ec = lc.getExperiment();
        if (ec != null) {
            GuiProjectSpace.PS.importCompound(ec);
        }
    }
}
