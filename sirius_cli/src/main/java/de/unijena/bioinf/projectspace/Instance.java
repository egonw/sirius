package de.unijena.bioinf.projectspace;

import de.unijena.bioinf.ChemistryBase.algorithm.scoring.FormulaScore;
import de.unijena.bioinf.ChemistryBase.algorithm.scoring.SScored;
import de.unijena.bioinf.ChemistryBase.ms.Ms2Experiment;
import de.unijena.bioinf.ChemistryBase.ms.ft.FTree;
import de.unijena.bioinf.ChemistryBase.ms.properties.FinalConfig;
import de.unijena.bioinf.ms.annotations.Annotated;
import de.unijena.bioinf.ms.annotations.DataAnnotation;
import de.unijena.bioinf.projectspace.sirius.CompoundContainer;
import de.unijena.bioinf.projectspace.sirius.FormulaResult;
import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class Instance {
    @NotNull
    protected final ProjectSpaceManager spaceManager;
    @NotNull
    private CompoundContainer compoundCache;

    protected Map<FormulaResultId, FormulaResult> formulaResultCache = new HashMap<>();

    protected Instance(@NotNull CompoundContainer compoundContainer, @NotNull ProjectSpaceManager spaceManager) {
        this.compoundCache = compoundContainer;
        this.spaceManager = spaceManager;
    }

    public final Ms2Experiment getExperiment() {
        return loadCompoundContainer(Ms2Experiment.class).getAnnotationOrThrow(Ms2Experiment.class);
    }

    public final CompoundContainerId getID() {
        return compoundCache.getId();
    }

    @Override
    public String toString() {
        return getID().toString();
    }

    SiriusProjectSpace projectSpace() {
        return getProjectSpaceManager().projectSpace();
    }

    public ProjectSpaceManager getProjectSpaceManager() {
        return spaceManager;
    }


    //load from projectSpace
    public final synchronized Optional<ProjectSpaceConfig> loadConfig() {
        return loadCompoundContainer(ProjectSpaceConfig.class).getAnnotation(ProjectSpaceConfig.class);
    }

    @SafeVarargs
    public final synchronized CompoundContainer loadCompoundContainer(Class<? extends DataAnnotation>... components) {
        try {
            Class[] missingComps = Arrays.stream(components).filter(comp -> !compoundCache.hasAnnotation(comp)).distinct().toArray(Class[]::new);
            if (missingComps.length > 0) { //load missing comps
                final CompoundContainer tmpComp = projectSpace().getCompound(getID(), missingComps);
                compoundCache.setAnnotationsFrom(tmpComp);
            }
            return compoundCache;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @SafeVarargs
    public final synchronized void reloadCompoundCache(Class<? extends DataAnnotation>... components) {
        try {
            compoundCache = projectSpace().getCompound(getID(), components);
        } catch (IOException e) {
            LoggerFactory.getLogger(Instance.class).error("Could not create read Input Experiment from Project Space.");
            throw new RuntimeException("Could not create read Input Experiment from Project Space.", e);
        }
    }

    @SafeVarargs
    public final synchronized FormulaResult loadFormulaResult(FormulaResultId fid, Class<? extends DataAnnotation>... components) {
        try {
            if (!formulaResultCache.containsKey(fid)) {
                if (!compoundCache.contains(fid))
                    LoggerFactory.getLogger(getClass()).warn("Unknown FormulaResultID, Either a wrong ID was inserted or the cached project-space was modified with bypassing the cache!");
                final FormulaResult fr = projectSpace().getFormulaResult(fid, components);
                formulaResultCache.put(fid, fr);
                return fr;
            } else {
                FormulaResult fr = formulaResultCache.get(fid);
                final Class[] missing = Arrays.stream(components).filter(comp -> !fr.hasAnnotation(comp)).toArray(Class[]::new);
                if (missing.length > 0)
                    fr.setAnnotationsFrom(projectSpace().getFormulaResult(fid, missing));

                return fr;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * @return Sorted List of FormulaResults scored by the currently defined RankingScore
     */
    @SafeVarargs
    public final synchronized List<? extends SScored<FormulaResult, ? extends FormulaScore>> loadFormulaResults(Class<? extends DataAnnotation>... components) {
        return loadFormulaResults(getID().getRankingScoreTypes(), components);
    }

    @SafeVarargs
    public final synchronized Optional<FormulaResult> loadTopFormulaResult(Class<? extends DataAnnotation>... components) {
        return getTop(loadFormulaResults(components));
    }

    @SafeVarargs
    public final synchronized Optional<FormulaResult> loadTopFormulaResult(List<Class<? extends FormulaScore>> rankingScoreTypes, Class<? extends DataAnnotation>... components) {
        return getTop(loadFormulaResults(rankingScoreTypes, components));
    }

    @SafeVarargs
    private Optional<FormulaResult> getTop(List<? extends SScored<FormulaResult, ? extends FormulaScore>> sScoreds, Class<? extends DataAnnotation>... components) {
        if (sScoreds.isEmpty()) return Optional.empty();
        else {
            FormulaResult candidate = sScoreds.get(0).getCandidate();
            return Optional.of(loadFormulaResult(candidate.getId(), components));
        }
    }

    @SafeVarargs
    public final synchronized List<? extends SScored<FormulaResult, ? extends FormulaScore>> loadFormulaResults(List<Class<? extends FormulaScore>> rankingScoreTypes, Class<? extends DataAnnotation>... components) {
        try {
            if (!formulaResultCache.keySet().containsAll(compoundCache.getResults().values())) {
                final List<? extends SScored<FormulaResult, ? extends FormulaScore>> returnList = projectSpace().getFormulaResultsOrderedBy(getID(), rankingScoreTypes, components);
                formulaResultCache = returnList.stream().collect(Collectors.toMap(r -> r.getCandidate().getId(), SScored::getCandidate));
                return returnList;
            } else {
                final Map<FormulaResultId, Class[]> toRefresh = new HashMap<>();
                formulaResultCache.forEach((k, v) -> {
                    Class[] missingComps = Arrays.stream(components).filter(c -> !v.hasAnnotation(c)).distinct().toArray(Class[]::new);
                    if (missingComps.length > 0)
                        toRefresh.put(k, missingComps);
                });

//                if (!toRefresh.isEmpty())
//                    System.out.println("######## refreshing components of '" + toRefresh.keySet().toString() + "' #########");

                //refresh annotations
                toRefresh.forEach((k, v) -> {
                    try {
                        final FormulaResult fr = projectSpace().getFormulaResult(k, v);
                        formulaResultCache.get(k).setAnnotationsFrom(fr);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });

                //return updated an sorted formula results
                return FormulaScoring.rankBy(formulaResultCache.values(), rankingScoreTypes, true);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    //write to projectSpace
    @SafeVarargs
    public final synchronized void updateCompound(CompoundContainer container, Class<? extends DataAnnotation>... components) {
        try {
            updateAnnotations(compoundCache, container, components);
            projectSpace().updateCompound(compoundCache, components);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @SafeVarargs
    public final synchronized void updateFormulaResult(FormulaResult result, Class<? extends DataAnnotation>... components) {
        try {
            if (!formulaResultCache.containsKey(result.getId())) {
                formulaResultCache.put(result.getId(), result);
                compoundCache.getResults().put(result.getId().fileName(), result.getId());
            }
            //refresh cache to actual object state?
            final FormulaResult rs = formulaResultCache.get(result.getId());
            updateAnnotations(rs, result, components);
            projectSpace().updateFormulaResult(rs, components);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized void updateExperiment() {
        updateCompound(compoundCache, Ms2Experiment.class);
    }

    public synchronized void updateConfig() {
        compoundCache.setAnnotation(ProjectSpaceConfig.class, new ProjectSpaceConfig(getExperiment().getAnnotationOrThrow(FinalConfig.class).config));
        updateCompound(compoundCache, ProjectSpaceConfig.class);
    }

    public synchronized void updateCompoundID() {
        try {
            projectSpace().updateCompoundContainerID(compoundCache.getId());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @SafeVarargs
    public final synchronized void deleteFromFormulaResults(Class<? extends DataAnnotation>... components) {
        if (components.length == 0)
            return;
        //remove stuff from memory copy before removing from disk to ensure that is done before property change is
        //fired by the project space
        if (List.of(components).contains(FTree.class)) {
            deleteFormulaResults();
        } else {
            //update cache, load data from disc
            List<FormulaResultId> rid = List.copyOf(loadCompoundContainer().getResults().values());
            //remove components from cached formula results
            formulaResultCache.forEach((k, v) -> List.of(components).forEach(v::removeAnnotation));
            //remove components from ALL formula results on disc
            rid.forEach(v -> {
                try {
                    projectSpace().deleteFromFormulaResult(v, components);
                } catch (IOException e) {
                    LoggerFactory.getLogger(getClass()).error("Error when deleting result '" + v + "' from '" + getID() + "'.");
                }
            });
        }






    }

    public synchronized void deleteFormulaResults() {
        //load contain methods to ensure that it is available
        List<FormulaResultId> rid = List.copyOf(loadCompoundContainer().getResults().values());

        compoundCache.getResults().clear();
        clearFormulaResultsCache();

        rid.forEach(v -> {
            try {
                projectSpace().deleteFormulaResult(v);
            } catch (IOException e) {
                LoggerFactory.getLogger(getClass()).error("Error when deleting result '" + v + "' from '" + getID() + "'.");
            }
        });
    }

    //remove from cache
    public synchronized void clearCompoundCache() {
        compoundCache.clearAnnotations();
    }

    @SafeVarargs
    public final synchronized void clearCompoundCache(Class<? extends DataAnnotation>... components) {
        if (compoundCache == null)
            return;

        for (Class<? extends DataAnnotation> component : components)
            compoundCache.removeAnnotation(component);
    }


    public synchronized void clearFormulaResultsCache() {
        formulaResultCache.clear();
    }

    @SafeVarargs
    public final synchronized void clearFormulaResultsCache(Class<? extends DataAnnotation>... components) {
        clearFormulaResultsCache(compoundCache.getResults().values(), components);
    }

    @SafeVarargs
    public final synchronized void clearFormulaResultsCache(Collection<FormulaResultId> results, Class<? extends DataAnnotation>... components) {
        if (components == null || components.length == 0)
            return;
        for (FormulaResultId result : results)
            clearFormulaResultCache(result, components);
    }

    @SafeVarargs
    public final synchronized void clearFormulaResultCache(FormulaResultId id, Class<? extends DataAnnotation>... components) {
        if (formulaResultCache.containsKey(id))
            for (Class<? extends DataAnnotation> comp : components)
                formulaResultCache.get(id).removeAnnotation(comp);
    }

    public synchronized Optional<FormulaResult> newFormulaResultWithUniqueId(FTree tree) {
        Optional<FormulaResult> frOpt = projectSpace().newFormulaResultWithUniqueId(compoundCache, tree);
        frOpt.ifPresent(fr -> formulaResultCache.put(fr.getId(), fr));
        return frOpt;
    }


    @SafeVarargs
    private <T extends DataAnnotation> void updateAnnotations(final Annotated<T> toRefresh, final Annotated<T> refresher, final Class<? extends DataAnnotation>... components) {
        if (toRefresh != refresher) {
            Set<Class<? extends DataAnnotation>> comps = Arrays.stream(components).collect(Collectors.toSet());
            refresher.annotations().forEach((k, v) -> {
                if (comps.contains(k))
                    toRefresh.setAnnotation(k, v);
            });
        }
    }
}