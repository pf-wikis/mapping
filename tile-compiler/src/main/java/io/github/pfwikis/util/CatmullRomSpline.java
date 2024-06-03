/*
 * www.javagl.de - Geom - Geometry utilities
 *
 * Copyright (c) 2013-2016 Marco Hutter - http://www.javagl.de
 * 
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 * 
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */
package io.github.pfwikis.util;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Simple implementation of an open Catmull-Rom-Spline
 */
public class CatmullRomSpline {
	// Parts of this implementation is roughly based on
	// http://en.wikipedia.org/wiki/Centripetal_Catmull%E2%80%93Rom_spline

	/**
	 * Creates a new Catmull-Rom-Spline with the given control points.
	 * 
	 * @param points          The control points. A deep copy of the given list will
	 *                        be created internally. Thus, changes in the given list
	 *                        or its points will not be reflected in this spline.
	 * @param stepsPerSegment The number of interpolation steps per segment
	 * @param alpha           The interpolation value. For 0.0, the spline is
	 *                        uniform. For 0.5, the spline is centripetal. For 1.0,
	 *                        the spline is chordal.
	 * @return The spline
	 */
	public static CatmullRomSpline create(List<? extends Point2D> points, int stepsPerSegment, double alpha) {
		return create(points, stepsPerSegment, alpha, false);
	}

	/**
	 * Creates a new Catmull-Rom-Spline with the given control points.
	 * 
	 * @param points          The control points. A deep copy of the given list will
	 *                        be created internally. Thus, changes in the given list
	 *                        or its points will not be reflected in this spline.
	 * @param stepsPerSegment The number of interpolation steps per segment
	 * @param alpha           The interpolation value. For 0.0, the spline is
	 *                        uniform. For 0.5, the spline is centripetal. For 1.0,
	 *                        the spline is chordal.
	 * @param closed          Whether the spline should be closed
	 * @return The spline
	 */
	public static CatmullRomSpline create(List<? extends Point2D> points, int stepsPerSegment, double alpha,
			boolean closed) {
		return new CatmullRomSpline(points, stepsPerSegment, alpha, closed);
	}

	/**
	 * The alpha value determining the interpolation: For 0.0, the spline is
	 * uniform. For 0.5, the spline is centripetal. For 1.0, the spline is chordal.
	 */
	private double alpha;

	/**
	 * The list of control points. This list contains copies of the points that are
	 * given in the constructor, as well as the additional points that are inserted
	 * before the first and after the last point.
	 */
	private final List<Point2D> controlPoints;

	/**
	 * The number of interpolation points between two control points
	 */
	private final int stepsPerSegment;

	/**
	 * The list of interpolated points
	 */
	private final List<Point2D> interpolatedPoints;

	/**
	 * Whether the control points have been modified, and the derived control points
	 * and the interpolated points have to be updated
	 */
	private boolean updateRequired = true;

	/**
	 * Whether this spline is closed
	 */
	private final boolean closed;

	/**
	 * Creates a new Catmull-Rom-Spline with the given points.
	 * 
	 * @param points          The control points. A deep copy of the given list will
	 *                        be created internally. Thus, changes in the given list
	 *                        or its points will not be reflected in this spline.
	 * @param stepsPerSegment The number of interpolation steps per segment
	 * @param alpha           The interpolation value. For 0.0, the spline is
	 *                        uniform. For 0.5, the spline is centripetal. For 1.0,
	 *                        the spline is chordal.
	 * @param closed          Whether the spline should by closed
	 */
	private CatmullRomSpline(List<? extends Point2D> points, int stepsPerSegment, double alpha, boolean closed) {
		this.stepsPerSegment = stepsPerSegment;
		this.alpha = alpha;
		int numInterpolatedPoints = (points.size() - 1) * stepsPerSegment + 1;
		if (closed) {
			numInterpolatedPoints += stepsPerSegment;
			this.controlPoints = createPoints(points.size() + 3);
			this.controlPoints.set(1, controlPoints.get(controlPoints.size() - 2));
		} else {
			this.controlPoints = createPoints(points.size() + 2);
		}
		this.interpolatedPoints = createPoints(numInterpolatedPoints);
		this.closed = closed;
		updateControlPoints(points);
	}

	/**
	 * Create a list containing the given number of points
	 * 
	 * @param n The number of points
	 * @return The list
	 */
	private static List<Point2D> createPoints(int n) {
		List<Point2D> points = new ArrayList<Point2D>();
		for (int i = 0; i < n; i++) {
			points.add(new Point2D.Double());
		}
		return points;
	}

	/**
	 * Set the alpha value determining the interpolation: For 0.0, the spline is
	 * uniform. For 0.5, the spline is centripetal. For 1.0, the spline is chordal.
	 * 
	 * @param alpha The alpha value
	 */
	public void setInterpolation(double alpha) {
		this.alpha = alpha;
		updateRequired = true;
	}

	/**
	 * Set the position of the specified control point
	 * 
	 * @param index The index of the point
	 * @param point The position that the control point will have afterwards
	 */
	void updateControlPoint(int index, Point2D point) {
		int numPoints = controlPoints.size() - (closed ? 3 : 2);
		if (index < 0) {
			throw new IndexOutOfBoundsException("Index " + index + " must be positive");
		}
		if (index >= controlPoints.size() - 1) {
			throw new IndexOutOfBoundsException(
					"Index was " + index + ", but number of control " + "points was " + numPoints);
		}
		Point2D cp = controlPoints.get(index + 1);
		cp.setLocation(point);
		updateRequired = true;
	}

	/**
	 * Set the positions of the the control points so that they match the given
	 * points. The number of points in the given list must be the same as the the
	 * number of points that was used to construct this spline.
	 * 
	 * @param points The control points
	 * @throws IllegalArgumentException If the number of points in the given list
	 *                                  does not match the number of points that was
	 *                                  given in the constructor
	 */
	public void updateControlPoints(List<? extends Point2D> points) {
		int numPoints = controlPoints.size() - (closed ? 3 : 2);
		if (points.size() != numPoints) {
			throw new IllegalArgumentException("Expected " + numPoints + " points, but got " + points.size());
		}
		for (int j = 0; j < points.size(); j++) {
			Point2D p = points.get(j);
			Point2D cp = controlPoints.get(j + 1);
			cp.setLocation(p);
		}
		updateRequired = true;
	}

	/**
	 * Returns an unmodifiable list containing the interpolated points. Note that
	 * although the list itself is unmodifiable, the contained points are still
	 * modifiable. Changes in these points will be overwritten by this class,
	 * although the exact conditions under which they will be overwritten are not
	 * specified.
	 * 
	 * @return The interpolated points
	 */
	public List<Point2D> getInterpolatedPoints() {
		validatePoints();
		return Collections.unmodifiableList(interpolatedPoints);
	}

	/**
	 * Make sure that the derived control points and the interpolated points are up
	 * to date referring to the current positions of the control points
	 */
	private void validatePoints() {
		if (updateRequired) {
			updateAdditionalControlPoints();
			updateInterpolatedPoints();
			updateRequired = false;
		}
	}

	/**
	 * Update the positions of the interpolated points, based on the current
	 * positions of the control points
	 */
	private void updateInterpolatedPoints() {
		int numPoints = controlPoints.size() - 2;
		for (int i = 0; i < numPoints - 1; i++) {
			int stepsInCurrentSegment = stepsPerSegment;
			int lastStepInSegment = stepsInCurrentSegment;
			if (i == numPoints - 2) {
				stepsInCurrentSegment++;
				lastStepInSegment = stepsInCurrentSegment - 1;
			}
			updateInterpolatedPoints(i, stepsInCurrentSegment, lastStepInSegment);
		}
	}

	/**
	 * Update the first and the last control point, based on the positions of the
	 * succeeding or preceding points
	 */
	private void updateAdditionalControlPoints() {
		if (closed) {
			Point2D py = controlPoints.get(controlPoints.size() - 3);
			Point2D cp0 = controlPoints.get(0);
			cp0.setLocation(py);

			Point2D p1 = controlPoints.get(2);
			Point2D cpz = controlPoints.get(controlPoints.size() - 1);
			cpz.setLocation(p1);
		} else {
			Point2D p0 = controlPoints.get(1);
			Point2D p1 = controlPoints.get(2);
			Point2D cp0 = controlPoints.get(0);
			sub(p1, p0, cp0);
			sub(p0, cp0, cp0);

			Point2D py = controlPoints.get(controlPoints.size() - 3);
			Point2D pz = controlPoints.get(controlPoints.size() - 2);
			Point2D cpz = controlPoints.get(controlPoints.size() - 1);
			sub(pz, py, cpz);
			add(pz, cpz, cpz);
		}

	}

	/**
	 * Computes the difference between the given points and stores it in the given
	 * result
	 * 
	 * @param p0     The first point
	 * @param p1     The second point
	 * @param result The result
	 */
	private static void sub(Point2D p0, Point2D p1, Point2D result) {
		result.setLocation(p0.getX() - p1.getX(), p0.getY() - p1.getY());
	}

	/**
	 * Computes the difference between the given points and stores it in the given
	 * result
	 * 
	 * @param p0     The first point
	 * @param p1     The second point
	 * @param result The result
	 */
	private static void add(Point2D p0, Point2D p1, Point2D result) {
		result.setLocation(p0.getX() + p1.getX(), p0.getY() + p1.getY());
	}

	/**
	 * Update the positions of the interpolated points based on the four control
	 * points starting at the given index
	 * 
	 * @param index                 The index in the list of control points
	 * @param stepsInCurrentSegment The number of steps in the current segment
	 * @param lastStepInSegment     The last step in the current segment.
	 */
	private void updateInterpolatedPoints(int index, int stepsInCurrentSegment, int lastStepInSegment) {
		Point2D p0 = controlPoints.get(index + 0);
		Point2D p1 = controlPoints.get(index + 1);
		Point2D p2 = controlPoints.get(index + 2);
		Point2D p3 = controlPoints.get(index + 3);
		double t0 = 0;
		double t1 = 1;
		double t2 = 2;
		double t3 = 3;
		if (alpha != 0.0) {
			double exponent = alpha * 0.5;
			double dx01 = p1.getX() - p0.getX();
			double dy01 = p1.getY() - p0.getY();
			double d01 = dx01 * dx01 + dy01 * dy01;
			t1 = t0 + Math.pow(d01, exponent);

			double dx12 = p2.getX() - p1.getX();
			double dy12 = p2.getY() - p1.getY();
			double d12 = dx12 * dx12 + dy12 * dy12;
			t2 = t1 + Math.pow(d12, exponent);

			double dx23 = p3.getX() - p2.getX();
			double dy23 = p3.getY() - p2.getY();
			double d23 = dx23 * dx23 + dy23 * dy23;
			t3 = t2 + Math.pow(d23, exponent);

			// System.out.println("Times "+t0+" "+t1+" "+t2+" "+t3);
		}
		double invStep = 1.0 / lastStepInSegment;
		for (int i = 0; i < stepsInCurrentSegment; i++) {
			double t = i * invStep;
			int interpolatedPointIndex = index * stepsPerSegment + i;
			Point2D interpolatedPoint = interpolatedPoints.get(interpolatedPointIndex);
			interpolate(p0, p1, p2, p3, t0, t1, t2, t3, t1 + t * (t2 - t1), interpolatedPoint);
		}
	}

	/**
	 * Perform the cubic Catmull-Rom-interpolation for the given control points and
	 * times
	 * 
	 * @param p0     The first point
	 * @param p1     The second point
	 * @param p2     The third point
	 * @param p3     The fourth point
	 * @param t0     The first time
	 * @param t1     The second time
	 * @param t2     The third time
	 * @param t3     The fourth time
	 * @param t      The current time
	 * @param result The point that will store the result
	 */
	private static void interpolate(Point2D p0, Point2D p1, Point2D p2, Point2D p3, double t0, double t1, double t2,
			double t3, double t, Point2D result) {
		double x0 = p0.getX();
		double y0 = p0.getY();
		double x1 = p1.getX();
		double y1 = p1.getY();
		double x2 = p2.getX();
		double y2 = p2.getY();
		double x3 = p3.getX();
		double y3 = p3.getY();
		double invDt01 = 1.0 / (t1 - t0);
		double invDt12 = 1.0 / (t2 - t1);
		double invDt23 = 1.0 / (t3 - t2);
		double f01a = (t1 - t) * invDt01;
		double f01b = (t - t0) * invDt01;
		double f12a = (t2 - t) * invDt12;
		double f12b = (t - t1) * invDt12;
		double f23a = (t3 - t) * invDt23;
		double f23b = (t - t2) * invDt23;
		double x01 = f01a * x0 + f01b * x1;
		double y01 = f01a * y0 + f01b * y1;
		double x12 = f12a * x1 + f12b * x2;
		double y12 = f12a * y1 + f12b * y2;
		double x23 = f23a * x2 + f23b * x3;
		double y23 = f23a * y2 + f23b * y3;
		double invDt02 = 1.0 / (t2 - t0);
		double invDt13 = 1.0 / (t3 - t1);
		double f012a = (t2 - t) * invDt02;
		double f012b = (t - t0) * invDt02;
		double f123a = (t3 - t) * invDt13;
		double f123b = (t - t1) * invDt13;
		double x012 = f012a * x01 + f012b * x12;
		double y012 = f012a * y01 + f012b * y12;
		double x123 = f123a * x12 + f123b * x23;
		double y123 = f123a * y12 + f123b * y23;
		double resultX = f12a * x012 + f12b * x123;
		double resultY = f12a * y012 + f12b * y123;
		// (Problem? :-D)
		result.setLocation(resultX, resultY);
	}

}