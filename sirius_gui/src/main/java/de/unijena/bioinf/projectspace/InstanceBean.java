package de.unijena.bioinf.projectspace;

import de.unijena.bioinf.ChemistryBase.algorithm.scoring.FormulaScore;
import de.unijena.bioinf.ChemistryBase.algorithm.scoring.SScored;
import de.unijena.bioinf.ChemistryBase.chem.MolecularFormula;
import de.unijena.bioinf.ChemistryBase.chem.PrecursorIonType;
import de.unijena.bioinf.ChemistryBase.ms.Ms2Experiment;
import de.unijena.bioinf.ChemistryBase.ms.MutableMs2Experiment;
import de.unijena.bioinf.ChemistryBase.ms.MutableMs2Spectrum;
import de.unijena.bioinf.ChemistryBase.ms.utils.SimpleSpectrum;
import de.unijena.bioinf.ms.frontend.core.SiriusPCS;
import de.unijena.bioinf.projectspace.sirius.CompoundContainer;
import de.unijena.bioinf.projectspace.sirius.FormulaResult;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This is the wrapper for the Instance class to interact with the gui
 * elements. It uses a special property change support that executes the events
 * in the EDT. So you can change all fields from any thread, the gui will still
 * be updated in the EDT. Some operations may NOT be Thread save, so you may have
 * to care about Synchronization.
 */
public class InstanceBean extends Instance implements SiriusPCS {
    private final MutableHiddenChangeSupport pcs = new MutableHiddenChangeSupport(this, true);
    public final AtomicBoolean computeLock = new AtomicBoolean(false);

    @Override
    public HiddenChangeSupport pcs() {
        return pcs;
    }

    //Project-space listener
    private List<ContainerListener.Defined> listeners;

//    private volatile ComputingStatus fingerIdComputeState = ComputingStatus.UNCOMPUTED;


    //todo best hit property change is needed.
    // e.g. if the scoring changes from sirius to zodiac

    //todo make compute state nice
    //todo we may nee background loading tasks for retriving informaion from project space

    //todo som unregister listener stategy

    public InstanceBean(@NotNull CompoundContainer compoundContainer, @NotNull ProjectSpaceManager spaceManager) {
        super(compoundContainer, spaceManager);
    }

    private List<ContainerListener.Defined> configureListeners() {
        final List<ContainerListener.Defined> listeners = new ArrayList<>(3);

        listeners.add(projectSpace().defineCompoundListener().onUpdate().onlyFor(Ms2Experiment.class).thenDo((event -> {
            if (!event.getAffectedID().equals(getID()))
                return;
            pcs.firePropertyChange("instance.ms2Experiment", null, event.getAffectedComponent(Ms2Experiment.class));
        })));

        listeners.add(projectSpace().defineFormulaResultListener().onCreate().thenDo((event -> {
            if (!event.getAffectedID().getParentId().equals(getID()))
                return;
            pcs.firePropertyChange("instance.createFormulaResult", null, event.getAffectedID());
        })));

        listeners.add(projectSpace().defineFormulaResultListener().onDelete().thenDo((event -> {
            if (!event.getAffectedID().getParentId().equals(getID()))
                return;
            pcs.firePropertyChange("instance.deleteFormulaResult", event.getAffectedID(), null);
        })));

        return listeners;

    }

    public void registerProjectSpaceListeners() {
        if (listeners == null)
            listeners = configureListeners();
        listeners.forEach(ContainerListener.Defined::register);

    }

    public void unregisterProjectSpaceListeners() {
        if (listeners == null)
            return;
        listeners.forEach(ContainerListener.Defined::unregister);
    }

    public String getName() {
        return getID().getCompoundName();
    }

    public String getGUIName() {
        return getName() + " (" + getID().getCompoundIndex() + ")";
    }

    public List<SimpleSpectrum> getMs1Spectra() {
        return getMutableExperiment().getMs1Spectra();
    }

    public List<MutableMs2Spectrum> getMs2Spectra() {
        return getMutableExperiment().getMs2Spectra();
    }

    public SimpleSpectrum getMergedMs1Spectrum() {
        return getMutableExperiment().getMergedMs1Spectrum();
    }

    public PrecursorIonType getIonization() {
        return getMutableExperiment().getPrecursorIonType();
    }

    public List<FormulaResultBean> getResults() {
        List<? extends SScored<FormulaResult, ? extends FormulaScore>> form = loadFormulaResults(FormulaScoring.class);
        return IntStream.range(0, form.size()).mapToObj(i -> new FormulaResultBean(form.get(i).getCandidate().getId(), this, i)).collect(Collectors.toList());
    }

    public double getIonMass() {
        return getID().getIonMass().orElse(Double.NaN);
    }

    // Computing State
    public boolean isComputing() {
        return computeLock.get();
    }

    public void setComputing(boolean computing) {
        pcs.firePropertyChange("computeState", computeLock.getAndSet(computing), computeLock.get());
    }

    private MutableMs2Experiment getMutableExperiment() {
        return (MutableMs2Experiment) getExperiment();
    }

    public Setter set() {
        return new Setter();
    }

    public class Setter {
        private List<Consumer<MutableMs2Experiment>> mods = new ArrayList<>();

        private Setter() {
        }

        // this is all MSExperiment update stuff. We listen to experiment changes on the project-space.
        // so calling updateExperiemnt will result in a EDT property change event if it was successful
        public Setter setName(final String name) {
            mods.add((exp) -> {
                if (projectSpace().renameCompound(getID(), name, (idx) -> spaceManager.namingScheme.apply(idx, name)))
                    exp.setName(name);
            });
            return this;
        }

        public Setter setIonization(final PrecursorIonType ionization) {
            mods.add((exp) -> exp.setPrecursorIonType(ionization));
            return this;
        }

        public Setter setIonMass(final double ionMass) {
            mods.add((exp) -> exp.setIonMass(ionMass));
            return this;
        }

        public Setter setMolecularFormula(final MolecularFormula formula) {
            mods.add((exp) -> exp.setMolecularFormula(formula));
            return this;
        }

        public void apply() {
            final MutableMs2Experiment exp = getMutableExperiment();
            for (Consumer<MutableMs2Experiment> mod : mods)
                mod.accept(exp);
            updateExperiment();
        }
    }
}
