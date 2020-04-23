package de.unijena.bioinf.ms.frontend.subtools;

import de.unijena.bioinf.ChemistryBase.jobs.SiriusJobs;
import de.unijena.bioinf.jjobs.JJob;
import de.unijena.bioinf.jjobs.JobSubmitter;
import de.unijena.bioinf.projectspace.Instance;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public abstract class DataSetJob extends ToolChainJobImpl<Iterable<Instance>> implements ToolChainJob<Iterable<Instance>> {
    private List<JJob<?>> failedJobs = new ArrayList<>();
    private List<Instance> failedInstances = new ArrayList<>();
    private List<Instance> inputInstances = new ArrayList<>();

    private final Predicate<Instance> inputValidator;

    public DataSetJob(@NotNull Predicate<Instance> inputValidator, @NotNull JobSubmitter submitter) {
        this(inputValidator, submitter, ReqJobFailBehaviour.WARN);
    }

    public DataSetJob(@NotNull Predicate<Instance> inputValidator, @NotNull JobSubmitter submitter, @NotNull ReqJobFailBehaviour failBehaviour) {
        super(submitter, failBehaviour);
        this.inputValidator = inputValidator;
    }

    @Override
    protected Iterable<Instance> compute() throws Exception {
        checkInputs();
        //todo maybe make decidable if any or all match
        final boolean hasResults = inputInstances.stream().anyMatch(this::isAlreadyComputed);
        final boolean recompute = inputInstances.stream().anyMatch(this::isRecompute);

        if (!hasResults || recompute) {
            if (hasResults) {
                updateProgress(0, 100, 2, "Invalidate existing Results and Recompute!");
                inputInstances.forEach(this::invalidateResults);
            }
            updateProgress(0, 100, 99, "Start computation...");
            inputInstances.forEach(this::enableRecompute); // enable recompute so that following tools will recompute if results exist.
            computeAndAnnotateResult(inputInstances);
            updateProgress(0, 100, 99, "DONE!");
        } else {
            updateProgress(0, 100, 99, "Skipping Job because results already Exist and recompute not requested.");
        }

        return inputInstances;
    }

    protected void checkInputs() {
        {
            final Map<Boolean, List<Instance>> splitted = inputInstances.stream().collect(Collectors.partitioningBy(inputValidator));
            inputInstances = splitted.get(true);
            failedInstances = splitted.get(false);
        }

        if (inputInstances == null || inputInstances.isEmpty())
            throw new IllegalArgumentException("No Input found, All dependent SubToolJobs are failed.");
        if (!failedJobs.isEmpty())
            logWarn("There are " + failedJobs.size() + " failed input providing InstanceJobs!"
                    + " Skipping Failed InstanceJobs: " + System.lineSeparator() + "\t"
                    + failedJobs.stream().map(JJob::identifier).collect(Collectors.joining(System.lineSeparator() + "\t"))
            );
        if (!failedInstances.isEmpty())
            logWarn("There are " + failedInstances.size() + " invalid input Instances!"
                    + " Skipping Invalid Input Instances: " + System.lineSeparator() + "\t"
                    + failedInstances.stream().map(Instance::toString).collect(Collectors.joining(System.lineSeparator() + "\t"))
            );
    }

    @Override
    protected void cleanup() {
        super.cleanup();
        failedJobs = null;
    }

    @Override
    public synchronized void handleFinishedRequiredJob(JJob required) {
        if (required instanceof InstanceJob) {
            final Object r = required.result();
            if (r == null)
                failedJobs.add(required);
            else
                inputInstances.add((Instance) r);
        }
    }


    protected abstract void computeAndAnnotateResult(final @NotNull List<Instance> expRes) throws Exception;

    public List<JJob<?>> getFailedJobs() {
        return failedJobs;
    }

    public boolean hasFailedInstances() {
        return failedJobs != null && !failedJobs.isEmpty();
    }


    public static class Factory<T extends DataSetJob> extends ToolChainJob.FactoryImpl<T> {
        public Factory(@NotNull Function<JobSubmitter, T> jobCreator, @NotNull Consumer<Instance> baseInvalidator) {
            super(jobCreator, baseInvalidator);
        }

        public T createToolJob(Iterable<JJob<Instance>> dataSet) {
            return createToolJob(dataSet, SiriusJobs.getGlobalJobManager());
        }

        public T createToolJob(Iterable<JJob<Instance>> dataSet, @NotNull JobSubmitter jobSubmitter) {
            final T job = makeJob(jobSubmitter);
            dataSet.forEach(job::addRequiredJob);
            return job;
        }
    }


}
