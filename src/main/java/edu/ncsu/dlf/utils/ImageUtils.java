package edu.ncsu.dlf.utils;


import org.apache.pdfbox.pdmodel.common.PDRectangle;
import javax.imageio.ImageIO;
import java.io.IOException;
import java.awt.image.BufferedImage;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.RenderedImage;
import java.io.File;
import java.util.Arrays;

/**
 * Utils class that handles getting an image of the the area surrounding the
 * PDF annotation to provide context
 * @author Team19
 */
public class ImageUtils {

    private static Color highlightColor = new Color(234, 249, 35, 140);
    private static boolean DEBUG = Boolean.parseBoolean(System.getenv("DEBUG"));

    private static final int BORDER_WIDTH = 30;
    private static final float SCALE_UP_FACTOR = 2.0f;
 
    //Used to describe different Markup types
    private enum PostExtractMarkup {
        NONE(1), HIGHLIGHTS(1), POPUP(2);
  
        public final float subImageContextMultiplier;
  
        PostExtractMarkup(float subImageContextMultiplier) {
            this.subImageContextMultiplier = subImageContextMultiplier;
        }
    }

    /**
     * @param image Image of the entire page the annotation is on
     * @param r Rectangle describe the area of the page image the annotation takes up
     * @return Plain image of the annotation and the area surrounding it
     */
    public static BufferedImage makePlainSubImage(BufferedImage image, PDRectangle r) {
        float[] convertedQuadPoints = rectToQuadArray(r);
        return makeSubImage(image, convertedQuadPoints, PostExtractMarkup.NONE, null);
    }
  
    /**
     * @param image Image of the entire page the annotation is on
     * @param quadPoints Describe the area of the page image the annotation takes up using (x,y) coordinates
     * @return Image of the highlight annotation and the area surrounding it
     */
    public static BufferedImage makeHighlightedSubImage(BufferedImage img, float[] quadPoints) {
        return makeSubImage(img, quadPoints, PostExtractMarkup.HIGHLIGHTS, null);
    }
  
     /**
     * @param image Image of the entire page the annotation is on
     * @param r Rectangle describe the area of the page image the annotation takes up
     * @param commentBoxImage Image of the comment box from Adobe Reader
     * @return Image of the popup annotation and the area surrounding it
     */
    public static BufferedImage makePopupSubImage(BufferedImage img, PDRectangle r, BufferedImage commentBoxImage) {
        float[] convertedQuadPoints = rectToQuadArray(r);
        return makeSubImage(img, convertedQuadPoints, PostExtractMarkup.POPUP, commentBoxImage);
    }
  
    /**
     * @param r Rectangle describe the area of the page image the annotation takes up
     * @return A float array describing (x,y) coordinates of the corners of a rectangle
     */
    private static float[] rectToQuadArray(PDRectangle r) {
        float[] convertedQuadPoints = new float[]{r.getLowerLeftX(),r.getLowerLeftY(),
                r.getUpperRightX(), r.getLowerLeftY(), r.getLowerLeftX(), r.getUpperRightY(),
                r.getUpperRightX(), r.getUpperRightY()};
        return convertedQuadPoints;
    }

   /**
    * Adapted the specs of a pdf tool http://www.pdf-technologies.com/api/html/P_PDFTech_PDFMarkupAnnotation_QuadPoints.htm
    * The QuadPoints array must contain 8*n elements specifying the coordinates of n quadrilaterals.
    * Each quadrilateral encompasses a word or group of continuous words in the text underlying the annotation.
    * The coordinates for each quadrilateral are given in the order x4 y4 x3 y3 x1 y1 x2 y2 specifying the quadrilateral's
    * four vertices  x1 is upper left and numbering goes clockwise.
    *
    * I assume all quadrilaterals are rectangles.  No guarantees on multi-column selects
    *
    * @param img Image of the page the annotation is found on
    * @param quadPoints Describe the area of the page image the annotation takes up using (x,y) coordinates
    * @param markup Markup enum type
    * @param commentBoxImage Image of the comment box from Adobe Reader
    * @return An image of the annotation and the area surrounding it
    */
  private static BufferedImage makeSubImage(BufferedImage img, float[] quadPoints, PostExtractMarkup markup, BufferedImage commentBoxImage) {
    if (quadPoints.length < 8) {
        return null;
    }

    Rectangle subImageRect = scaleAndTransformSubImageQuad(quadPoints, img, markup);

    BufferedImage subImage = img.getSubimage(subImageRect.x, subImageRect.y, subImageRect.width, subImageRect.height);

    BufferedImage newImage = new BufferedImage(subImage.getWidth(), subImage.getHeight(), img.getType());
    Graphics2D g2 = newImage.createGraphics();

    g2.drawImage(subImage, 0, 0, null);

    if (markup == PostExtractMarkup.HIGHLIGHTS) {
        for (int n = 0; n < quadPoints.length; n += 8) {
            float[] oneQuad = Arrays.copyOfRange(quadPoints, n, n + 8);

            paintHighlight(g2, scaleAndTransformAnnotationQuad(oneQuad, subImageRect, img.getHeight()));
        }
    } else if (markup == PostExtractMarkup.POPUP) {
        //we know quadPoints will be only one quad because that's how makePopupSubImage defines it.
        if(commentBoxImage == null) {
           // throw new Exception("Comment box image not available");
        }
        paintCommentBox(g2, scaleAndTransformAnnotationQuad(quadPoints, subImageRect, img.getHeight()), commentBoxImage);
    }

    g2.dispose();

    if (DEBUG) {
        try {
            // for debugging
            File output = new File("test"+Math.random()+".png");
            System.out.println("Saving image to disk "+output.getAbsolutePath());
            ImageIO.write(newImage, "png", output);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    return newImage;
}

    /**
     * Scale and transform the sub image to provide enough context to the user
     * @param quadPoints Describe the area of the page image the annotation takes up using (x,y) coordinates
     * @param img An image of the annotation and the area surrounding it
     * @param markup PostExtractMarkup enum type
     */
    private static Rectangle scaleAndTransformSubImageQuad(float[] quadPoints, RenderedImage img, PostExtractMarkup markup) {
        // we find the upper left corner
        int minX = getMinXFromQuadPoints(quadPoints);
        int minY = getMinYFromQuadPoints(quadPoints);

        // allows us to make
        float scaledBorder = BORDER_WIDTH * markup.subImageContextMultiplier;

        int x = Math.round((minX - scaledBorder) * SCALE_UP_FACTOR);
        x = Math.max(x, 0); // keep subimage on screen
        int y = Math.round((minY - scaledBorder) * SCALE_UP_FACTOR);
        y = Math.max(y, 0); // keep subimage on screen

        int width = Math.round((getMaxXFromQuadPoints(quadPoints) - minX + 2 * scaledBorder) * SCALE_UP_FACTOR);
        width = Math.min(width, img.getWidth() - x); // clamp width
        int height = Math.round((getMaxYFromQuadPoints(quadPoints) - minY + 2 * scaledBorder) * SCALE_UP_FACTOR);
        height = Math.min(height, img.getHeight() - y); // clamp height

        // the y is counted from the bottom, so we have to flip our coordinate
        y = (img.getHeight() - y - height);
        return new Rectangle(x, y, width, height);
    }

    /**
     * Scale and transform the rectangle representing the original annotation
     * @param oneQuad Quad points representing the original annotation
     * @param boundingRect Boundary of the image
     * @param imageHeight Height of the image of the annotation
     */
    private static Rectangle scaleAndTransformAnnotationQuad(float[] oneQuad, Rectangle boundingRect, int imageHeight) {
        int x = getMinXFromQuadPoints(oneQuad);
        int y = getMinYFromQuadPoints(oneQuad);

        int width = Math.round((getMaxXFromQuadPoints(oneQuad) - x) * SCALE_UP_FACTOR);
        int height = Math.round((getMaxYFromQuadPoints(oneQuad) - y) * SCALE_UP_FACTOR);

        x *= SCALE_UP_FACTOR;
        y *= SCALE_UP_FACTOR;

        x -= boundingRect.x;
        // invert y again
        y = imageHeight - y - boundingRect.y - height;

        return new Rectangle(x, y, width, height);
    }

    /**
     * Paints or draws a comment box using Graphics2D
     * @param g2 Graphics2D object based on the size of the orginal annotation
     * @param rect Rectangle based on the size of the orignal annotation
     * @param commentBoxImage Image of a comment box from Adobe Reader
     */
    private static void paintCommentBox(Graphics2D g2, Rectangle rect, BufferedImage commentBoxImage) {
        if (commentBoxImage != null) {
            g2.drawImage(commentBoxImage, rect.x, rect.y, rect.width, rect.height, null);
        } else {
            g2.setColor(highlightColor);
            g2.setStroke(new BasicStroke(2 * SCALE_UP_FACTOR));
            g2.drawRect(rect.x, rect.y , rect.width, rect.height);
        }

    }

    /**
     * Fills a rectangle with the color of a highlight
     * @param g2 Graphics2D object based on the size of the orginal annotation
     * @param rect Rectangle based on the size of the orignal annotation
     */
    private static void paintHighlight(Graphics2D g2, Rectangle rect) {
        g2.setColor(highlightColor);
        g2.fillRect(rect.x, rect.y , rect.width, rect.height);
    }
    
    /**
     * X values are on the even integers
     * @param quadPoints representing the corners of a quadrilateral using (x,y) coordinates
     * @return Minimum x coordinate value
     */
    private static int getMinXFromQuadPoints(float[] quadPoints) {
        int min = Integer.MAX_VALUE;
        for(int i = 0; i< quadPoints.length; i += 2) {
            if (quadPoints[i] < min) {
                min = (int)quadPoints[i];
            }
        }
        return min;
    }

    /**
     * Y values are on the odd integers
     * @param quadPoints representing the corners of a quadrilateral using (x,y) coordinates
     * @return Minimum y coordinate value
     */
    private static int getMinYFromQuadPoints(float[] quadPoints) {
        int min = Integer.MAX_VALUE;
        for(int i = 1; i< quadPoints.length; i += 2) {
            if (quadPoints[i] < min) {
                min = (int)quadPoints[i];
            }
        }
        return min;
    }

    /**
     * X values are on the even integers
     * @param quadPoints representing the corners of a quadrilateral using (x,y) coordinates
     * @return Maximum x coordinate value
     */
    private static int getMaxXFromQuadPoints(float[] quadPoints) {
        int max = 0;
        for(int i = 0; i< quadPoints.length; i += 2) {
            if (quadPoints[i] > max) {
                max = (int)quadPoints[i];
            }
        }
        return max;
    }

    /**
     * Y values are on the odd integers
     * @param quadPoints representing the corners of a quadrilateral using (x,y) coordinates
     * @return Maxium Y coordinate value
     */
    private static int getMaxYFromQuadPoints(float[] quadPoints) {
        int max = 0;
        for(int i = 1; i< quadPoints.length; i += 2) {
            if (quadPoints[i] > max) {
                max = (int)quadPoints[i];
            }
        }
        return max;
    }

}
