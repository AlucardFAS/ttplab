package solver;

import ttp.TTP1Instance;
import ttp.TTPSolution;
import utils.Deb;
import utils.RandGen;


/**
 * Created by kyu on 4/7/15.
 */
public class Cosolver2SA extends CosolverBase {

  public Cosolver2SA() {
    super();
  }

  public Cosolver2SA(TTP1Instance ttp) {
    super(ttp);
  }



  @Override
  public TTPSolution solve() {

    //===============================================
    // generate initial solution
    //===============================================
    Constructive construct = new Constructive(ttp);
    TTPSolution s0 = construct.generate("lz");
    // pre-process the knapsack
    // insert and eliminate items
    s0 = insertAndEliminate(s0);
    //===============================================

    // copy initial solution into improved solution
    TTPSolution sol = s0.clone();


    // best found
    double GBest = sol.ob;
    // number of iterations
    int nbIter = 0;
    // improvement tag
    boolean improved;

    //===============================================
    // start cosolver search
    //===============================================
    do {
      nbIter++;
      improved = false;

      // 2-opt heuristic on TSKP
      sol = ls2opt(sol);

      // simple bit-flip on KRP
      sol = simulatedAnnealing(sol);

      // update best if improvement
      if (sol.ob > GBest) {
        GBest = sol.ob;
        improved = true;
      }

      // stop execution if interrupted
      if (Thread.currentThread().isInterrupted()) return sol;

      // debug msg
      if (this.debug) {
        Deb.echo("Best "+nbIter+":");
        //Deb.echo(sol);
        Deb.echo("ob-best: "+sol.ob);
        Deb.echo("wend   : "+sol.wend);
        Deb.echo("---");
      }

      // stop when no improvements
    } while (improved);
    //===============================================

    return sol;
  }




  /**
   * deal with the KRP sub-problem
   * this function applies a simple bit-flip
   */
  public TTPSolution simulatedAnnealing(TTPSolution sol) {

    // copy initial solution into improved solution
    TTPSolution sBest = sol.clone();

    // TTP data
    int nbCities = ttp.getNbCities();
    int nbItems = ttp.getNbItems();
    int[] A = ttp.getAvailability();
    double maxSpeed = ttp.getMaxSpeed();
    double minSpeed = ttp.getMinSpeed();
    long capacity = ttp.getCapacity();
    double C = (maxSpeed - minSpeed) / capacity;
    double R = ttp.getRent();

    // initial solution data
    int[] tour = sol.getTour();
    int[] pickingPlan = sol.getPickingPlan();

    // delta parameters
    int deltaP, deltaW;

    // best solution
    double GBest = sol.ob;

    // neighbor solution
    long fp;
    double ft, G;
    long wc;
    int origBF;
    int k, r;
    int nbIter = 0;

    // SA params
    double T_abs = 1;
    double T = 100.0;
    double alpha = .95;
    double trialFactor;
    if (nbItems < 500)
      trialFactor = 1000;
    else if (nbItems < 1000)
      trialFactor = 100;
    else if (nbItems < 5000)
      trialFactor = 50;
    else if (nbItems < 20000)
      trialFactor = 10; //was 5... retest others
    else if (nbItems < 100000)
      trialFactor = 1;
    else if (nbItems < 200000)
      trialFactor = .04;
    else
      trialFactor = .03;

    //if (nbCities > 50000)
    //  trialFactor = .1;

    long trials = Math.round(nbItems*trialFactor);

    Deb.echo(">>>> TRIAL FACTOR: "+trialFactor);

    //===============================================
    // start simulated annealing process
    //===============================================
    do {
      nbIter++;

      // cleanup and stop execution if interrupted
      if (Thread.currentThread().isInterrupted()) break;

      for (int u=0; u<trials; u++) {

        // browse items randomly
        k = RandGen.randInt(0, nbItems - 1);

        // check if new weight doesn't exceed knapsack capacity
        if (pickingPlan[k] == 0 && ttp.weightOf(k) > sol.wend) continue;

        // calculate deltaP and deltaW
        if (pickingPlan[k] == 0) {
          deltaP = ttp.profitOf(k);
          deltaW = ttp.weightOf(k);
        } else {
          deltaP = -ttp.profitOf(k);
          deltaW = -ttp.weightOf(k);
        }
        fp = sol.fp + deltaP;

        // handle velocity constraint
        // index where Bit-Flip happened
        origBF = sol.mapCI[A[k] - 1];
        // starting time
        ft = origBF == 0 ? .0 : sol.timeAcc[origBF - 1];
        // recalculate velocities from bit-flip city
        // to recover objective value
        for (r = origBF; r < nbCities; r++) {
          wc = sol.weightAcc[r] + deltaW;
          ft += ttp.distFor(tour[r] - 1, tour[(r + 1) % nbCities] - 1) / (maxSpeed - wc * C);
        }
        // compute recovered objective value
        G = fp - ft * R;

        //=====================================
        // update if improvement or
        // Boltzmann condition satisfied
        //=====================================
        double mu = Math.random();
        double energy_gap = G - GBest;
        boolean acceptance = energy_gap > 0 || Math.exp(energy_gap / T) > mu;
        if (acceptance) {

          GBest = G;

          // bit-flip
          pickingPlan[k] = pickingPlan[k] != 0 ? 0 : A[k];

          //===========================================================
          // recover accumulation vectors
          //===========================================================
          if (pickingPlan[k] != 0) {
            deltaP = ttp.profitOf(k);
            deltaW = ttp.weightOf(k);
          } else {
            deltaP = -ttp.profitOf(k);
            deltaW = -ttp.weightOf(k);
          }
          fp = sol.fp + deltaP;
          origBF = sol.mapCI[A[k] - 1];
          ft = origBF == 0 ? 0 : sol.timeAcc[origBF - 1];
          for (r = origBF; r < nbCities; r++) {
            // recalculate velocities from bit-flip city
            wc = sol.weightAcc[r] + deltaW;
            ft += ttp.distFor(tour[r] - 1, tour[(r + 1) % nbCities] - 1) / (maxSpeed - wc * C);
            // recover wacc and tacc
            sol.weightAcc[r] = wc;
            sol.timeAcc[r] = ft;
          }
          G = fp - ft * R;
          sol.ob = G;
          sol.fp = fp;
          sol.ft = ft;
          sol.wend = capacity - sol.weightAcc[nbCities - 1];
          //===========================================================

        }

      }

      // update best if improvement
      if (sol.ob > sBest.ob) {
        sBest = sol.clone();
      }

      if (this.debug) {
        Deb.echo(">> KRP " + nbIter + ": ob=" +
          String.format("%.0f",sol.ob));
      }

      // cool down temperature
      T = T * alpha;

      // stop when temperature reach absolute value
    } while (T > T_abs);


    // in order to recover all history vector
    ttp.objective(sBest);

    return sBest;
  }


}
