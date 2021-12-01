package de.unijena.bioinf.ms.gui.fingerid.custom_db;

import de.unijena.bioinf.chemdb.custom.CustomDataSources;
import de.unijena.bioinf.ms.frontend.subtools.custom_db.CustomDBOptions;
import de.unijena.bioinf.ms.gui.compute.DBSelectionList;
import de.unijena.bioinf.ms.gui.compute.SubToolConfigPanel;
import de.unijena.bioinf.ms.gui.utils.GuiUtils;
import de.unijena.bioinf.ms.gui.utils.PlaceholderTextField;
import de.unijena.bioinf.ms.gui.utils.TextHeaderBoxPanel;
import de.unijena.bioinf.ms.gui.utils.TwoColumnPanel;
import de.unijena.bioinf.ms.gui.utils.jCheckboxList.JCheckboxListPanel;
import de.unijena.bioinf.storage.blob.Compressible;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;


public class DatabaseImportConfigPanel extends SubToolConfigPanel<CustomDBOptions> {

    private final JCheckboxListPanel<CustomDataSources.Source> parentDBList;
    public final PlaceholderTextField nameField;
    JComboBox<Compressible.Compression> compression;
    JSpinner bufferSize;

    public DatabaseImportConfigPanel() {
        this(null);
    }

    public DatabaseImportConfigPanel(@Nullable String dbName) {
        super(CustomDBOptions.class);

        final TwoColumnPanel smalls = new TwoColumnPanel();
        add(new TextHeaderBoxPanel("Parameters", smalls));

        this.nameField = new PlaceholderTextField(20);
        if (dbName == null){
            nameField.setPlaceholder("Enter name (no whitespaces)");
        }else {
            nameField.setText(dbName);

        }
        nameField.setEnabled(dbName == null);


        getOptionDescriptionByName("location").ifPresent(it -> nameField.setToolTipText(GuiUtils.formatToolTip(it)));
        smalls.addNamed("Location", nameField);
        parameterBindings.put("location", nameField::getText);

        final String buf = "buffer";
        bufferSize = makeGenericOptionSpinner(buf,
                getOptionDefaultByName(buf).map(Integer::parseInt).orElse(1),
                1, Integer.MAX_VALUE, 1,
                (v) -> String.valueOf(v.getNumber().intValue()));
        smalls.addNamed("Buffer Size", bufferSize);
        compression = makeGenericOptionComboBox("compression", Compressible.Compression.class);
        smalls.addNamed("Compression", compression);


        // configure database to derive from
        parentDBList = new JCheckboxListPanel<>(new DBSelectionList(false), "Derive DB from:");
        getOptionDescriptionByName("derive-from").ifPresent(it -> parentDBList.setToolTipText(GuiUtils.formatToolTip(it)));
        add(parentDBList);
        parameterBindings.put("derive-from", () -> parentDBList.checkBoxList.getCheckedItems().isEmpty() ? null : String.join(",", ((DBSelectionList) parentDBList.checkBoxList).getSelectedFormulaSearchDBStrings()));
    }
}
