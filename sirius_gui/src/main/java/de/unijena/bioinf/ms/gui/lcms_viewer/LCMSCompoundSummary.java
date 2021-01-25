/*
 * This file is part of the SIRIUS library for analyzing MS and MS/MS data
 *
 * Copyright (C) 2013-2020 Kai Dührkop, Markus Fleischauer, Marcus Ludwig, Martin A. Hoffman and Sebastian Böcker, Chair of Bioinformatics, Friedrich-Schilller University.
 *
 * This library is free software; you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation; either version 3 of the License, or (at your option) any later version.
 * This library is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with SIRIUS. If not, see <https://www.gnu.org/licenses/lgpl-3.0.txt>
 */

package de.unijena.bioinf.ms.gui.lcms_viewer;

import de.unijena.bioinf.ChemistryBase.chem.ChemicalAlphabet;
import de.unijena.bioinf.ChemistryBase.chem.FormulaConstraints;
import de.unijena.bioinf.ChemistryBase.chem.MolecularFormula;
import de.unijena.bioinf.ChemistryBase.chem.PrecursorIonType;
import de.unijena.bioinf.ChemistryBase.math.Statistics;
import de.unijena.bioinf.ChemistryBase.ms.*;
import de.unijena.bioinf.ChemistryBase.ms.lcms.CoelutingTraceSet;
import de.unijena.bioinf.ChemistryBase.ms.lcms.IonTrace;
import de.unijena.bioinf.ChemistryBase.ms.lcms.Trace;
import de.unijena.bioinf.ChemistryBase.ms.utils.Spectrums;
import de.unijena.bioinf.MassDecomposer.Chemistry.MassToFormulaDecomposer;

import java.util.*;

public class LCMSCompoundSummary {

    protected CoelutingTraceSet traceSet;
    protected IonTrace compoundTrace;
    protected Ms2Experiment ms2Experiment;

    protected ArrayList<Check> peakCheck, isotopeCheck, adductCheck, ms2Check;
    Quality peakQuality, isotopeQuality, adductQuality, ms2Quality;

    public LCMSCompoundSummary(CoelutingTraceSet traceSet, IonTrace compoundTrace, Ms2Experiment ms2Experiment) {
        this.traceSet = traceSet;
        this.compoundTrace = compoundTrace;
        this.ms2Experiment = ms2Experiment;
        checkPeak();
        checkIsotopes();
        //checkAdducts();
        checkMs2();
    }

    private void checkMs2() {
        ms2Check=new ArrayList<>();
        if (ms2Experiment!=null) {
            final MassToFormulaDecomposer mfd = new MassToFormulaDecomposer(
                    new ChemicalAlphabet(MolecularFormula.parseOrThrow("CHNOPS").elementArray())
            );
            final FormulaConstraints fcr = new FormulaConstraints("CHNOPS");
            final Deviation dev = new Deviation(5);
            final PrecursorIonType ionType = PrecursorIonType.getPrecursorIonType("[M+H]+");
            // check number of peaks and number of peaks with intensity above 3%
            int npeaks=0, nIntensivePeaks=0, total = 0;
            final HashSet<CollisionEnergy> ces = new HashSet<>();
            final Optional<NoiseInformation> noiseModel = ms2Experiment.getAnnotation(NoiseInformation.class);
            for (Ms2Spectrum<Peak> ms2 : ms2Experiment.getMs2Spectra()) {
                ces.add(ms2.getCollisionEnergy());
                final double mx = Spectrums.getMaximalIntensity(ms2);
                final double threshold = noiseModel.map(NoiseInformation::getSignalLevel).orElse(mx*0.03);
                for (int k=0; k < ms2.size(); ++k) {
                    final double mass = ionType.precursorMassToNeutralMass(ms2.getMzAt(k));
                    if (mass >= ms2Experiment.getIonMass()-10)
                        continue;
                    final Iterator<MolecularFormula> molecularFormulaIterator = mfd.neutralMassFormulaIterator(mass, dev, fcr);
                    boolean found=false;
                    while (molecularFormulaIterator.hasNext()) {
                        final MolecularFormula F = molecularFormulaIterator.next();
                        if (F.rdbe()>=0) {
                            found=true;
                            break;
                        }
                    }

                    if (found) {
                        if (ms2.getIntensityAt(k) >= threshold) {
                            ++nIntensivePeaks;
                        }
                        ++npeaks;
                    }
                    ++total;
                }
            }

            int score = 0;

            if (npeaks >= 20) {
                ms2Check.add(new Check(
                        Quality.GOOD, "Ms/Ms has " + npeaks + " decomposable peaks."
                ));
                score += 5;
            } else if (npeaks >= 10) {
                ms2Check.add(new Check(
                        Quality.MEDIUM, "Ms/Ms has " + npeaks + " decomposable peaks."
                ));
                score += 3;
            } else {
                ms2Check.add(new Check(
                        Quality.LOW, "Ms/Ms has only " + npeaks + " decomposable peaks."
                ));
                score += 1;
            }

            if (nIntensivePeaks >= 6) {
                ms2Check.add(new Check(
                        Quality.GOOD, "Ms/Ms has " + nIntensivePeaks + (noiseModel.isPresent() ? " peaks above the noise level." : " peaks with relative intensity above 3%.")
                ));
                score += 3;
            } else if (nIntensivePeaks >= 3) {
                ms2Check.add(new Check(
                        Quality.MEDIUM, "Ms/Ms has " + nIntensivePeaks + (noiseModel.isPresent() ? " peaks above the noise level." : " peaks with relative intensity above 3%.")
                ));
                score += 2;
            } else {
                ms2Check.add(new Check(
                        Quality.LOW, "Ms/Ms has " + (nIntensivePeaks == 0 ? "no" : (nIntensivePeaks==1) ? "only one" : "only " + nIntensivePeaks) + (noiseModel.isPresent() ? " peaks above the noise level." : " peaks with relative intensity above 3%.")
                ));
                score += 1;
            }
            if (ces.size()>1) {
                ms2Check.add(new Check(
                        Quality.GOOD, "Ms/Ms has " + ces.size() + " different collision energies."
                ));
                score += 3;
            } else {
                ms2Check.add(new Check(
                        Quality.LOW, "Ms/Ms was only recorded at a single collision energy."
                ));
            }
            ms2Quality = (score >= 6) ? Quality.GOOD : (score >= 3 ? Quality.MEDIUM : Quality.LOW);
        }
    }


    private static String[] words = new String[]{
            "First","Second","Third","Fourth","Fifth"
    };

    private void checkIsotopes() {
        this.isotopeCheck = new ArrayList<>();

        // check number if isotope peaks
        if (compoundTrace.getIsotopes().length>=3) {
            isotopeQuality=Quality.GOOD;
            isotopeCheck.add(new Check(
                    Quality.GOOD, "Has " + compoundTrace.getIsotopes().length + " isotope peaks."
            ));

        } else if (compoundTrace.getIsotopes().length>=2) {
            isotopeQuality=Quality.MEDIUM;
            isotopeCheck.add(new Check(
                    Quality.MEDIUM, "Has two isotope peaks."
            ));
        } else {
            isotopeCheck.add(new Check(
                    Quality.LOW, "Has no isotope peaks besides the monoisotopic peak."
            ));
            this.isotopeQuality = Quality.LOW;
            return;
        }

        int goodIsotopePeaks = 0;
        final Trace m = compoundTrace.getIsotopes()[0];
        checkIsotopes:
        for (int k=1; k < compoundTrace.getIsotopes().length; ++k) {
            final Trace t = compoundTrace.getIsotopes()[k];
            double correlation = 0d;
            double[] iso = new double[t.getDetectedFeatureLength()];
            double[] main = iso.clone();
            int j=0;
            eachPeak:
            for (int i=t.getDetectedFeatureOffset(),n=t.getDetectedFeatureOffset()+ t.getDetectedFeatureLength(); i<n; ++i) {
                final int mainIndex = i+t.getIndexOffset()-m.getIndexOffset();
                if (mainIndex < 0 || mainIndex >= m.getIntensities().length) {
                    if (t.getIntensities()[i] <  traceSet.getNoiseLevels()[i+t.getIndexOffset()]) {
                        continue eachPeak;
                    } else {
                        isotopeCheck.add(new Check(
                                Quality.LOW, "The isotope peak is found at retention times outside of the monoisotopic peak"
                        ));
                        break checkIsotopes;
                    }
                }
                main[j] = m.getIntensities()[mainIndex];
                iso[j++] = t.getIntensities()[i];
            }
            if (j < iso.length) {
                main = Arrays.copyOf(main, j);
                iso = Arrays.copyOf(iso,j);
            }
            correlation = Statistics.pearson(main, iso);
            // we also use our isotope scoring
            final double intensityScore = scoreIso(main, iso);
            Quality quality;
            if (correlation>=0.99 || intensityScore>=5) quality=Quality.GOOD;
            else if (correlation>=0.95 || intensityScore>=0) quality=Quality.MEDIUM;
            else quality = Quality.LOW;

            isotopeCheck.add(new Check(quality, String.format(Locale.US, "%s isotope peak has a correlation of %.2f. The isotope score is %.3f.", getNumWord(k), correlation, intensityScore)));
            if (quality==Quality.GOOD) ++goodIsotopePeaks;
        }

        if (goodIsotopePeaks>=2) {
            isotopeQuality = Quality.GOOD;
        }

    }

    private static String getNumWord(int i) {
        if (i-1 < words.length) return words[i-1];
        else return i + "th";
    }

    private double scoreIso(double[] a, double[] b) {
        double mxLeft = a[0];
        for (int i=1; i < a.length; ++i) {
            mxLeft = Math.max(a[i],mxLeft);
        }
        double mxRight = b[0];
        for (int i=1; i < b.length; ++i) {
            mxRight = Math.max(b[i],mxRight);
        }

        double sigmaA = Math.max(0.02, traceSet.getNoiseLevels()[compoundTrace.getMonoisotopicPeak().getAbsoluteIndexApex()]/Math.max(mxLeft,mxRight));
        double sigmaR = 0.05;

        double score = 0d;
        for (int i=0; i < a.length; ++i) {
            final double ai = a[i]/mxLeft;
            final double bi = b[i]/mxRight;
            final double delta = bi-ai;

            double peakPropbability = Math.log(Math.exp(-(delta*delta)/(2*(sigmaA*sigmaA + bi*bi*sigmaR*sigmaR)))/(2*Math.PI*bi*sigmaR*sigmaR));
            final double sigma = 2*bi*sigmaR + 2*sigmaA;
            score += peakPropbability-Math.log(Math.exp(-(sigma*sigma)/(2*(sigmaA*sigmaA + bi*bi*sigmaR*sigmaR)))/(2*Math.PI*bi*sigmaR*sigmaR));
        }

        return score;


    }

    private void checkPeak() {
        this.peakCheck = new ArrayList<>();
        final Trace t = compoundTrace.getMonoisotopicPeak();

        // has a clearly defined start point
        float apex = t.getApexIntensity();
        float begin = t.getLeftEdgeIntensity();
        float ende = t.getRightEdgeIntensity();

        // check if there is another peak on the left side
        boolean leftNeighbour = false;
        if (t.getDetectedFeatureOffset()>1) {
            float[] xs = t.getIntensities();
            int j = t.getDetectedFeatureOffset();
            if (xs[j] < xs[j-1] && xs[j-1] < xs[j-2] && (xs[j-2]-xs[j]) > traceSet.getNoiseLevels()[j] ) {
                leftNeighbour = true;
            }
        }

        // check if there is another peak on the right side
        boolean rightNeighbour = false;
        if (t.getDetectedFeatureOffset()+t.getDetectedFeatureLength()+1 <t.getIntensities().length ) {
            float[] xs = t.getIntensities();
            int j = t.getDetectedFeatureOffset()+t.getDetectedFeatureLength()-1;
            if (xs[j] < xs[j+1] && xs[j+1] < xs[j+2] && (xs[j+2]-xs[j]) > traceSet.getNoiseLevels()[j] ) {
                rightNeighbour = true;
            }
        }

        boolean relativeBegin = begin/apex <= 0.2;
        boolean absoluteBegin = begin <= traceSet.getNoiseLevels()[t.absoluteIndexLeft()]*20;

        boolean relativeEnd = ende/apex <= 0.2;
        boolean absoluteEnd = ende <= traceSet.getNoiseLevels()[t.absoluteIndexRight()]*20;

        boolean slopeLeft = (apex-begin)/apex >= 0.2;
        boolean slopeRight = (apex-ende)/apex >= 0.2;

        leftNeighbour &= slopeLeft;
        rightNeighbour &= slopeRight;

        if (relativeBegin&&absoluteBegin && relativeEnd&&absoluteEnd) {
            peakCheck.add(new Check(Quality.GOOD, "The chromatographic peak has clearly defined start and end points."));
        } else {
            if (relativeBegin&&(absoluteBegin||leftNeighbour)) {
                peakCheck.add(new Check(Quality.GOOD, "The chromatographic peak has clearly defined start points."));
            } else if (relativeBegin) {
                peakCheck.add(new Check(Quality.MEDIUM, "The chromatographic peak starts way above the noise level."));
            } else if (absoluteBegin) {
                peakCheck.add(new Check(Quality.MEDIUM, "The left side of the chromatographic peak is close to noise level"));
            } else if (leftNeighbour) {
                peakCheck.add(new Check(Quality.MEDIUM, "The left edge of the chromatographic peak is clearly separated from its left neighbour peak"));
            } else {
                peakCheck.add(new Check(Quality.LOW, "The left edge of the chromatographic peak is not well defined."));
            }
            if (relativeEnd&&(absoluteEnd||rightNeighbour)) {
                peakCheck.add(new Check(Quality.GOOD, "The chromatographic peak has clearly defined end points."));
            } else if (relativeEnd) {
                peakCheck.add(new Check(Quality.MEDIUM, "The chromatographic peak ends way above the noise level."));
            } else if (absoluteEnd) {
                peakCheck.add(new Check(Quality.MEDIUM, "The right side of the chromatographic peak is close to noise level"));
            } else if (rightNeighbour) {
                peakCheck.add(new Check(Quality.MEDIUM, "The right edge of the chromatographic peak is clearly separated from its right neighbour peak"));
            }  else {
                peakCheck.add(new Check(Quality.LOW, "The right edge of the chromatographic peak is not well defined."));
            }


        }

        // check number of data points
        if (t.getDetectedFeatureLength() >= 8) {
            peakCheck.add(new Check(Quality.GOOD, "The chromatographic peak consists of " + t.getDetectedFeatureLength() + " data points"));
        } else if (t.getDetectedFeatureLength() >= 4) {
            peakCheck.add(new Check(Quality.MEDIUM, "The chromatographic peak consists of " + t.getDetectedFeatureLength() + " data points"));
        } else peakCheck.add(new Check(Quality.LOW, "The chromatographic peak has only " + t.getDetectedFeatureLength() + " data points"));

        // check peak slope

        Quality quality = Quality.GOOD;
        for (Check c : peakCheck) {
            if (c.quality.ordinal() < quality.ordinal()) {
                quality = c.quality;
            }
        }
        this.peakQuality = quality;
    }

    public static enum Quality {
        LOW, MEDIUM, GOOD;
    }

    public static class Check {
        private final Quality quality;
        private final String description;

        public Check(Quality quality, String description) {
            this.quality = quality;
            this.description = description;
        }

        public Quality getQuality() {
            return quality;
        }

        public String getDescription() {
            return description;
        }
    }
}
