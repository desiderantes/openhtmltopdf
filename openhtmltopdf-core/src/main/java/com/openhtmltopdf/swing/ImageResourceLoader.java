package com.openhtmltopdf.swing;

import java.awt.image.BufferedImage;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;

import javax.imageio.ImageIO;

import com.openhtmltopdf.extend.FSImage;
import com.openhtmltopdf.resource.ImageResource;
import com.openhtmltopdf.util.*;


/**
 *
 */
public class ImageResourceLoader {
    public static final RepaintListener NO_OP_REPAINT_LISTENER = doLayout -> XRLog.log(Level.FINE, LogMessageId.LogMessageId0Param.GENERAL_NO_OP_REPAINT_REQUESTED);
    private final Map<CacheKey, ImageResource> _imageCache;

    private final ImageLoadQueue _loadQueue;

    private final int _imageCacheCapacity;

    private RepaintListener _repaintListener = NO_OP_REPAINT_LISTENER;

    private final boolean _useBackgroundImageLoading;

    public ImageResourceLoader() {
        // FIXME
        this(16);
    }

    public ImageResourceLoader(int cacheSize) {
        this._imageCacheCapacity = cacheSize;
        this._useBackgroundImageLoading = Configuration.isTrue("xr.image.background.loading.enable", false);

        if (_useBackgroundImageLoading) {
            this._loadQueue = new ImageLoadQueue();
            final int workerCount = Configuration.valueAsInt("xr.image.background.workers", 5);
            for (int i = 0; i < workerCount; i++) {
                new ImageLoadWorker(_loadQueue).start();
            }
        } else {
            this._loadQueue = null;
        }

        this._repaintListener = NO_OP_REPAINT_LISTENER;

        // note we do *not* override removeEldestEntry() here--users of this class must call shrinkImageCache().
        // that's because we don't know when is a good time to flush the cache
        this._imageCache = new LinkedHashMap<>(cacheSize, 0.75f, true);
    }

    public static ImageResource loadImageResourceFromUri(final String uri) {
        if (ImageUtil.isEmbeddedBase64Image(uri)) {
            return loadEmbeddedBase64ImageResource(uri);
        } else {
            StreamResource sr = new StreamResource(uri);
            InputStream is;
            ImageResource ir = null;
            try {
                sr.connect();
                is = sr.bufferedStream();
                try {
                    BufferedImage img = ImageIO.read(is);
                    if (img == null) {
                        throw new IOException("ImageIO.read() returned null");
                    }
                    ir = createImageResource(uri, img);
                } catch (FileNotFoundException e) {
                    XRLog.log(Level.WARNING, LogMessageId.LogMessageId1Param.EXCEPTION_CANT_READ_IMAGE_FILE_FOR_URI_NOT_FOUND, uri);
                } catch (IOException e) {
                    XRLog.log(Level.WARNING, LogMessageId.LogMessageId1Param.EXCEPTION_CANT_READ_IMAGE_FILE_FOR_URI, uri, e);
                } finally {
                    sr.close();
                }
            } catch (IOException e) {
                // couldnt open stream at URI...
                XRLog.log(Level.WARNING, LogMessageId.LogMessageId2Param.EXCEPTION_CANT_OPEN_STREAM_FOR_URI, uri, e.getMessage());
            }
            if (ir == null) {
                ir = createImageResource(uri, null);
            }
            return ir;
        }
    }
    
    public static ImageResource loadEmbeddedBase64ImageResource(final String uri) {
        BufferedImage bufferedImage = ImageUtil.loadEmbeddedBase64Image(uri);
        if (bufferedImage != null) {
            FSImage image = AWTFSImage.createImage(bufferedImage);
            return new ImageResource(null, image);
        } else {
            return new ImageResource(null, null);
        }
    }

    public synchronized void shrink() {
        int ovr = _imageCache.size() - _imageCacheCapacity;
        Iterator<CacheKey> it = _imageCache.keySet().iterator();
        while (it.hasNext() && ovr-- > 0) {
            it.next();
            it.remove();
        }
    }

    public synchronized void clear() {
        _imageCache.clear();
    }

    public ImageResource get(final String uri) {
        return get(uri, -1, -1);
    }

    public synchronized ImageResource get(final String uri, final int width, final int height) {
        if (ImageUtil.isEmbeddedBase64Image(uri)) {
            ImageResource resource = loadEmbeddedBase64ImageResource(uri);
            resource.getImage().scale(width, height);
            return resource;
        } else {
            CacheKey key = new CacheKey(uri, width, height);
            ImageResource ir = _imageCache.get(key);
            if (ir == null) {
                // not loaded, or not loaded at target size

                // loaded a base size?
                ir = _imageCache.get(new CacheKey(uri, -1, -1));

                // no: loaded
                if (ir == null) {
                    if (isImmediateLoadUri(uri)) {
                        XRLog.log(Level.FINE, LogMessageId.LogMessageId1Param.LOAD_LOAD_IMMEDIATE_URI, uri);
                        ir = loadImageResourceFromUri(uri);
                        FSImage awtfsImage = ir.getImage();
                        BufferedImage newImg = ((AWTFSImage) awtfsImage).getImage();
                        loaded(ir, -1, -1);
                        if (width > -1 && height > -1) {
                            XRLog.log(Level.FINE, LogMessageId.LogMessageId4Param.LOAD_IMAGE_LOADER_SCALING_URI_TO, this, uri, width, height);
                            newImg = ImageUtil.getScaledInstance(newImg, width, height);
                            ir = new ImageResource(ir.getImageUri(), AWTFSImage.createImage(newImg));
                            loaded(ir, width, height);
                        }
                    } else {
                        XRLog.log(Level.FINE, LogMessageId.LogMessageId1Param.LOAD_IMAGE_CACHE_MISS_QUEUEING, uri);
                        MutableFSImage mfsi = new MutableFSImage(_repaintListener);
                        ir = new ImageResource(uri, mfsi);
                        _loadQueue.addToQueue(this, uri, mfsi, width, height);
                    }

                    _imageCache.put(key, ir);
                } else {
                    // loaded at base size, need to scale
                    XRLog.log(Level.FINE, LogMessageId.LogMessageId4Param.LOAD_IMAGE_LOADER_SCALING_URI_TO, this, uri, width, height);
                    FSImage awtfsImage = ir.getImage();
                    BufferedImage newImg = ((AWTFSImage) awtfsImage).getImage();

                    newImg = ImageUtil.getScaledInstance(newImg, width, height);
                    ir = new ImageResource(ir.getImageUri(), AWTFSImage.createImage(newImg));
                    loaded(ir, width, height);
                }
            }
            return ir;
        }
    }

    public boolean isImmediateLoadUri(final String uri) {
        return ! _useBackgroundImageLoading || uri.startsWith("jar:file:") || uri.startsWith("file:");
    }

    public synchronized void loaded(final ImageResource ir, final int width, final int height) {
        String imageUri = ir.getImageUri();
        if (imageUri != null) {
            _imageCache.put(new CacheKey(imageUri, width, height), ir);
        }
    }

    public static ImageResource createImageResource(final String uri, final BufferedImage img) {
        if (img == null) {
            return new ImageResource(uri, AWTFSImage.createImage(ImageUtil.createTransparentImage(10, 10)));
        } else {
            return new ImageResource(uri, AWTFSImage.createImage(ImageUtil.makeCompatible(img)));
        }
    }

    public void setRepaintListener(final RepaintListener repaintListener) {
        _repaintListener = repaintListener;
    }

    public void stopLoading() {
        if (_loadQueue != null) {
            XRLog.log(Level.INFO, LogMessageId.LogMessageId1Param.LOAD_CLEARING_PENDING_ITEMS_FROM_LOAD_QUEUE, _loadQueue.size());
            _loadQueue.reset();
        }
    }

    private static class CacheKey {
        final String uri;
        final int width;
        final int height;

        public CacheKey(final String uri, final int width, final int height) {
            this.uri = uri;
            this.width = width;
            this.height = height;
        }

        public boolean equals(final Object o) {
            if (this == o) return true;
            if (!(o instanceof CacheKey)) return false;

            final CacheKey cacheKey = (CacheKey) o;

            if (height != cacheKey.height) return false;
            if (width != cacheKey.width) return false;
            if (!uri.equals(cacheKey.uri)) return false;

            return true;
        }

        public int hashCode() {
            int result = uri.hashCode();
            result = 31 * result + width;
            result = 31 * result + height;
            return result;
        }
    }
}

// from-io-loader
// from-cache-loader
// from-fs-loader
