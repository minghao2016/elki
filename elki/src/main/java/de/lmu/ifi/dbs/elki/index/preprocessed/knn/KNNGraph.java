package de.lmu.ifi.dbs.elki.index.preprocessed.knn;
/*
 This file is part of ELKI:
 Environment for Developing KDD-Applications Supported by Index-Structures

 Copyright (C) 2016
 Ludwig-Maximilians-Universität München
 Lehr- und Forschungseinheit für Datenbanksysteme
 ELKI Development Team

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import de.lmu.ifi.dbs.elki.database.datastore.memory.MapStore;
import de.lmu.ifi.dbs.elki.database.ids.DBID;
import de.lmu.ifi.dbs.elki.database.ids.DBIDIter;
import de.lmu.ifi.dbs.elki.database.ids.DBIDMIter;
import de.lmu.ifi.dbs.elki.database.ids.DBIDUtil;
import de.lmu.ifi.dbs.elki.database.ids.DBIDs;
import de.lmu.ifi.dbs.elki.database.ids.HashSetModifiableDBIDs;
import de.lmu.ifi.dbs.elki.database.ids.KNNHeap;
import de.lmu.ifi.dbs.elki.database.ids.KNNList;
import de.lmu.ifi.dbs.elki.database.query.distance.DistanceQuery;
import de.lmu.ifi.dbs.elki.database.relation.Relation;
import de.lmu.ifi.dbs.elki.distance.distancefunction.DistanceFunction;
import de.lmu.ifi.dbs.elki.logging.Logging;
import de.lmu.ifi.dbs.elki.logging.progress.FiniteProgress;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.OptionID;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameterization.Parameterization;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.RandomParameter;
import de.lmu.ifi.dbs.elki.utilities.random.RandomFactory;

public class KNNGraph<O> extends AbstractMaterializeKNNPreprocessor<O> {
  /**
   * Logger
   */
  private static final Logging LOG = Logging.getLogger(KNNGraph.class);

  
  /**
   * Random generator
   */
  private final RandomFactory rnd;

  /**
   * Constructor.
   *
   * @param relation Relation to index
   * @param distanceFunction distance function
   * @param k k
   * @param rnd Random generator
   */
  public KNNGraph(Relation<O> relation, DistanceFunction<? super O> distanceFunction, int k, RandomFactory rnd) {
    super(relation, distanceFunction, k);
    this.rnd = rnd;
  }
  
  @Override
  public void logStatistics() {
    // TODO Auto-generated method stub
    
  }

  @Override
  protected void preprocess() {
    DistanceQuery<O> distanceQuery = relation.getDistanceQuery(distanceFunction);
    storage = new MapStore<KNNList>();
    MapStore<KNNHeap> store = new MapStore<KNNHeap>();
    FiniteProgress progress = getLogger().isVerbose() ? new FiniteProgress("Materializing KNN-Graph (k=" + k + ")", relation.size(), getLogger()) : null;

    //TODO avoid double storage
    for(DBIDIter iditer = relation.iterDBIDs(); iditer.valid(); iditer.advance()) {
      //TODO single-threaded random?
      final DBIDs sample = DBIDUtil.randomSample(relation.getDBIDs(), k, rnd);
      KNNHeap heap = DBIDUtil.newHeap(k);
      for (DBIDIter siter = sample.iter(); siter.valid(); siter.advance()){
        heap.insert(distanceQuery.distance(iditer, siter), siter);
      }
      store.put(iditer,heap);
    }
    int counter = 1;
    
    MapStore<HashSetModifiableDBIDs> trueNeighborHash = new MapStore<HashSetModifiableDBIDs>();    
    HashSetModifiableDBIDs allNeighbors = DBIDUtil.newHashSet(2*k);
    while (counter != 0){
      for(DBIDIter iditer = relation.iterDBIDs(); iditer.valid(); iditer.advance()) {
        //build reverse neighbors
        DBID id = DBIDUtil.deref(iditer);
        DBIDs neighbors = store.get(iditer).toKNNList();
        DBIDs rev = reverse(id,store);
        //join with neighbors
        allNeighbors.addDBIDs(neighbors);
        allNeighbors.addDBIDs(rev);
        trueNeighborHash.put(id, allNeighbors);
        //TODO use other types so that unorderedIterator for KNNHeap is used
//         // KNNList has contains-method
//         KNNList list = get(iditer);
//         list.contains(o);
        
        
      }
      counter = 0;
      //iterate through dataset
      for(DBIDIter iditer = relation.iterDBIDs(); iditer.valid(); iditer.advance()) {
        //for every neighbor of a neighbor do
        DBID id = DBIDUtil.deref(iditer);
        KNNHeap newNeighbors = store.get(id);
        HashSetModifiableDBIDs trueNeighbors = trueNeighborHash.get(id);
        for(DBIDMIter neighboriter = trueNeighbors.iter(); neighboriter.valid(); neighboriter.advance()) {
          DBID neighbor = DBIDUtil.deref(neighboriter);
          HashSetModifiableDBIDs nNeighbors = trueNeighborHash.get(neighbor);
          for (DBIDMIter nniter = nNeighbors.iter(); nniter.valid(); nniter.advance()){
            DBID nn = DBIDUtil.deref(nniter);
            if (nn.compareTo(id)!= 0){
                //calculate similarity of v and u2
                double distance = distanceQuery.distance(neighbor,nn);
                // TODO Heap should have always the same size --> contains-method?
                // TODO 1 if heap is changed, 0 if not --> also better with contains-method
                double newDistance = newNeighbors.insert(distance, id);
//                System.out.println("distance: "+distance+", newDistance: "+newDistance);
                if (newDistance == 0){
                  System.out.println(id.compareTo(nn));
                }
                if (distance <= newDistance){
                  counter=1;
                }
            } 
          }
        }
      }
      allNeighbors.clear();
      trueNeighborHash.clear();
      getLogger().incrementProcessed(progress);
    }
    //convert store to storage
    for(DBIDIter iditer = relation.iterDBIDs(); iditer.valid(); iditer.advance()) {
      KNNHeap heap = store.get(iditer);
      KNNList list = heap.toKNNList();
      storage.put(iditer, list);
    }
    getLogger().ensureCompleted(progress);
  }

  private HashSetModifiableDBIDs reverse(DBID id, MapStore<KNNHeap> store) {
    HashSetModifiableDBIDs rev = DBIDUtil.newHashSet(k);
    for(DBIDIter iditer = relation.iterDBIDs(); iditer.valid(); iditer.advance()) {
      KNNList neighbors = store.get(iditer).toKNNList();
      if (neighbors.contains(id)){
        rev.add(id);
      }
    }
    return rev;
  }

  @Override
  protected Logging getLogger() {
    return LOG;
  }

  @Override
  public String getLongName() {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public String getShortName() {
    // TODO Auto-generated method stub
    return null;
  }
  
  public static class Factory<O> extends AbstractMaterializeKNNPreprocessor.Factory<O> {
    /**
     * Random generator
     */
    private final RandomFactory rnd;

    /**
     * Constructor.
     *
     * @param k K
     * @param distanceFunction distance function
     * @param rnd Random generator
     */
    public Factory(int k, DistanceFunction<? super O> distanceFunction, RandomFactory rnd) {
      super(k, distanceFunction);
      this.rnd = rnd;
    }

    @Override
    public KNNGraph<O> instantiate(Relation<O> relation) {
      return new KNNGraph<>(relation, distanceFunction, k, rnd);
    }

    /**
     * Parameterization class
     *
     * @author Erich Schubert
     *
     * @apiviz.exclude
     *
     * @param <O> Object type
     */
    public static class Parameterizer<O> extends AbstractMaterializeKNNPreprocessor.Factory.Parameterizer<O> {
      /**
       * Random number generator seed.
       *
       * <p>
       * Key: {@code -knngraph.seed}
       * </p>
       */
      public static final OptionID SEED_ID = new OptionID("randomknn.seed", "The random number seed.");
      
      /**
       * Random generator
       */
      private RandomFactory rnd;

      @Override
      protected void makeOptions(Parameterization config) {
        super.makeOptions(config);
        RandomParameter rndP = new RandomParameter(SEED_ID);
        if(config.grab(rndP)) {
          rnd = rndP.getValue();
        }
      }

      @Override
      protected KNNGraph.Factory<O> makeInstance() {
        return new KNNGraph.Factory<>(k, distanceFunction, rnd);
      }
    }
  }
}



