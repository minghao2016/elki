package de.lmu.ifi.dbs.elki.database.lucene;

/*
 This file is part of ELKI:
 Environment for Developing KDD-Applications Supported by Index-Structures

 Copyright (C) 2013
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
import org.apache.lucene.search.DefaultSimilarity;
import org.apache.lucene.search.Similarity;

import de.lmu.ifi.dbs.elki.database.ids.DBIDRef;
import de.lmu.ifi.dbs.elki.distance.distancefunction.AbstractDBIDDistanceFunction;
import de.lmu.ifi.dbs.elki.utilities.exceptions.AbortException;

/**
 * Distance function, interfacing back into Lucene.
 * 
 * @author Erich Schubert
 */
public class LuceneDistanceFunction extends AbstractDBIDDistanceFunction {
  /**
   * Lucene similarity.
   */
  Similarity sim = new DefaultSimilarity();

  @Override
  public double distance(DBIDRef id1, DBIDRef id2) {
    // FIXME: how to compute the same similarity value that lucene uses?
    throw new AbortException("Manual distance computations are not yet implemented!");
  }
}
