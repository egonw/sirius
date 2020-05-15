package de.unijena.bioinf.ms.gui.actions;
/**
 * Created by Markus Fleischauer (markus.fleischauer@gmail.com)
 * as part of the sirius_frontend
 * 29.01.17.
 */

import de.unijena.bioinf.ms.gui.compute.jjobs.Jobs;
import de.unijena.bioinf.ms.gui.configs.Icons;

import javax.swing.*;
import java.awt.event.ActionEvent;

import static de.unijena.bioinf.ms.gui.mainframe.MainFrame.MF;

/**
 * @author Markus Fleischauer (markus.fleischauer@gmail.com)
 */
public class ShowJobsDialogAction extends AbstractAction {

    public ShowJobsDialogAction() {
        super("Jobs");
        putValue(Action.LARGE_ICON_KEY, Icons.FB_LOADER_STOP_32);
        putValue(Action.SHORT_DESCRIPTION, "Show background jobs and their status");

        //Listen if there are active gui jobs
        Jobs.MANAGER.getJobs().addListEventListener(listChanges -> {
            if (getValue(Action.LARGE_ICON_KEY).equals(Icons.FB_LOADER_STOP_32)) {
                if (!Jobs.MANAGER.getJobs().isEmpty() && Jobs.MANAGER.hasActiveJobs())
                    putValue(Action.LARGE_ICON_KEY, Icons.FB_LOADER_RUN_32);
            } else {
                if (Jobs.MANAGER.getJobs().isEmpty() || !Jobs.MANAGER.hasActiveJobs())
                    putValue(Action.LARGE_ICON_KEY, Icons.FB_LOADER_STOP_32);
            }
        });
    }


    @Override
    public void actionPerformed(ActionEvent e) {
        MF.getJobDialog().setVisible(true);
    }
}
