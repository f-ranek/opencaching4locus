package org.bogus.domowygpx.services.html;

public interface ImageSourceResolver
{
    int SOURCE_MAIN = 1;
    int SOURCE_COMMENT = 2;
    String processImgSrc(String src, int sourcePlaceCode);
}
