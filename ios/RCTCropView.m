//
// RCTCropView.m
// react-native-image-crop-tools
//
// Created by Hunaid Hassan on 06/01/2020.
//

#import "RCTCropView.h"
#import <TOCropViewController/TOCropView.h>
#import <TOCropViewController/UIImage+CropRotate.h>
#import <Photos/Photos.h>

@implementation RCTCropView {
    TOCropView * _inlineCropView;
    UIImage * _originalImage; // Store original image for reset
    UIImage * _currentTransformedImage; // Store current transformed image with all transformations applied
    NSString * _lastSourceUrl; // Track source URL changes
    
    // State preservation variables
    CGFloat _currentRotationAngle; // Track total rotation
    CGRect _savedCropFrame;
    CGSize _savedImageSize;
    BOOL _hasValidSavedState;
}

@synthesize sourceUrl, keepAspectRatio, cropAspectRatio, iosDimensionSwapEnabled;

- (void)layoutSubviews {
    [super layoutSubviews];
    
    // Only create crop view if it doesn't exist OR if source URL changed
    BOOL needsNewCropView = (_inlineCropView == nil) || ![sourceUrl isEqualToString:_lastSourceUrl];
    
    if (needsNewCropView) {
        UIImage *image;
        
        if([sourceUrl rangeOfString:@"ph://"].location == 0) {
            NSString *url = [sourceUrl stringByReplacingOccurrencesOfString:@"ph://" withString:@""];
            PHImageRequestOptions * requestOptions = [[PHImageRequestOptions alloc] init];
            requestOptions.resizeMode = PHImageRequestOptionsResizeModeExact;
            requestOptions.deliveryMode = PHImageRequestOptionsDeliveryModeHighQualityFormat;
            requestOptions.synchronous = YES;
            PHAsset *photosAsset = [PHAsset fetchAssetsWithLocalIdentifiers:@[url] options: nil].lastObject;
            PHImageManager *manager = [PHImageManager defaultManager];
            __block UIImage *blockImage;
            [manager requestImageForAsset:photosAsset
                               targetSize:PHImageManagerMaximumSize
                              contentMode:PHImageContentModeDefault
                                  options:requestOptions
                            resultHandler:^void(UIImage *image, NSDictionary *info) {
                                blockImage = image;
                            }];
            image = blockImage;
        } else {
            image = [UIImage imageWithData:[NSData dataWithContentsOfURL:[NSURL URLWithString:sourceUrl]]];
        }
        
        // Check if this is a new source URL (new image)
        BOOL isNewSource = ![sourceUrl isEqualToString:_lastSourceUrl];
        
        if (isNewSource) {
            // New image - reset all state
            _originalImage = image;
            _currentTransformedImage = image;
            _lastSourceUrl = sourceUrl;
            _currentRotationAngle = 0;
            _hasValidSavedState = NO;
        }
        
        // Remove existing crop view if present
        if (_inlineCropView) {
            [_inlineCropView removeFromSuperview];
        }
        
        // Use current transformed image (preserves transformations)
        UIImage *imageToUse = _currentTransformedImage ?: image;
        
        _inlineCropView = [[TOCropView alloc] initWithImage:imageToUse];
        [self setupCropView];
        
        // Restore saved state if available
        if (_hasValidSavedState) {
            [self restoreCropViewState];
        }
    }

    // Always update frame to match current bounds
    if (_inlineCropView) {
        _inlineCropView.frame = self.bounds;
    }
}

- (UIImage *)getCroppedImage {
    return [_inlineCropView.image croppedImageWithFrame:_inlineCropView.imageCropFrame angle:_inlineCropView.angle circularClip:NO];
}

- (CGRect)getCropFrame {
    return _inlineCropView.imageCropFrame;
}

- (void)setCropAspectRatio:(CGSize)aspectRatio {
    if (_inlineCropView) {
        _inlineCropView.aspectRatio = aspectRatio;
    }
    self->cropAspectRatio = aspectRatio;
}

-(CGSize)cropAspectRatio {
    return _inlineCropView.aspectRatio;
}

- (void)setKeepAspectRatio:(BOOL)keepAspectRatio {
    if (_inlineCropView) {
        _inlineCropView.aspectRatioLockEnabled = keepAspectRatio;
    }
    self->keepAspectRatio = keepAspectRatio;
}

- (BOOL)keepAspectRatio {
    return _inlineCropView.aspectRatioLockEnabled;
}

- (void)setIosDimensionSwapEnabled:(BOOL)iosDimensionSwapEnabled {
    if (_inlineCropView) {
        _inlineCropView.aspectRatioLockDimensionSwapEnabled = iosDimensionSwapEnabled;
    }
    self->iosDimensionSwapEnabled = iosDimensionSwapEnabled;
}

- (BOOL)iosDimensionSwapEnabled {
    return _inlineCropView.aspectRatioLockDimensionSwapEnabled;
}

- (void)rotateImage:(BOOL)clockwise {
    if (!_inlineCropView) return;
    
    // Save current crop state
    [self saveCropViewState];
    
    // Apply rotation to the actual image
    CGFloat rotationAngle = clockwise ? 90.0 : -90.0;
    _currentRotationAngle += rotationAngle;
    
    // Normalize angle to 0-360 range
    while (_currentRotationAngle >= 360) _currentRotationAngle -= 360;
    while (_currentRotationAngle < 0) _currentRotationAngle += 360;
    
    // Apply the rotation to the current transformed image
    UIImage *rotatedImage = [self rotateImageByDegrees:_currentTransformedImage degrees:rotationAngle];
    _currentTransformedImage = rotatedImage;
    
    // Recreate the crop view with the rotated image
    [_inlineCropView removeFromSuperview];
    _inlineCropView = [[TOCropView alloc] initWithImage:rotatedImage];
    [self setupCropView];
    
    // The crop view now has the rotated image as its base, so no additional rotation needed
    _hasValidSavedState = NO;
}

- (void)flipImageHorizontally {
    if (!_inlineCropView || !_currentTransformedImage) return;
    
    // Save current crop state
    [self saveCropViewState];
    
    // Apply horizontal flip to the current transformed image
    UIImage *flippedImage = [self flipImage:_currentTransformedImage horizontal:YES];
    _currentTransformedImage = flippedImage;
    
    // Recreate the crop view with the flipped image
    [_inlineCropView removeFromSuperview];
    _inlineCropView = [[TOCropView alloc] initWithImage:flippedImage];
    [self setupCropView];
    
    // Try to restore a similar crop frame
    if (_hasValidSavedState && !CGRectIsEmpty(_savedCropFrame)) {
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(0.1 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
            [self restoreCropFrame];
        });
    }
}

- (void)flipImageVertically {
    if (!_inlineCropView || !_currentTransformedImage) return;
    
    // Save current crop state
    [self saveCropViewState];
    
    // Apply vertical flip to the current transformed image
    UIImage *flippedImage = [self flipImage:_currentTransformedImage horizontal:NO];
    _currentTransformedImage = flippedImage;
    
    // Recreate the crop view with the flipped image
    [_inlineCropView removeFromSuperview];
    _inlineCropView = [[TOCropView alloc] initWithImage:flippedImage];
    [self setupCropView];
    
    // Try to restore a similar crop frame
    if (_hasValidSavedState && !CGRectIsEmpty(_savedCropFrame)) {
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(0.1 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
            [self restoreCropFrame];
        });
    }
}

- (void)resetImage {
    if (_inlineCropView && _originalImage) {
        _currentTransformedImage = _originalImage;
        _currentRotationAngle = 0;
        _hasValidSavedState = NO;
        
        // Remove current crop view and recreate with original image
        [_inlineCropView removeFromSuperview];
        _inlineCropView = [[TOCropView alloc] initWithImage:_originalImage];
        [self setupCropView];
    }
}

// Helper method to save current crop view state
- (void)saveCropViewState {
    if (_inlineCropView) {
        _savedCropFrame = _inlineCropView.imageCropFrame;
        _savedImageSize = _inlineCropView.imageViewFrame.size;
        _hasValidSavedState = YES;
    }
}

// Helper method to restore crop frame after transformation
- (void)restoreCropFrame {
    if (!_inlineCropView || CGRectIsEmpty(_savedCropFrame)) return;
    
    // TOCropView doesn't expose direct crop frame setting
    // Instead, we'll reset to center and let user re-crop if needed
    [_inlineCropView moveCroppedContentToCenterAnimated:NO];
    
    // If we have a specific aspect ratio set, reapply it
    if (!CGSizeEqualToSize(self->cropAspectRatio, CGSizeZero)) {
        _inlineCropView.aspectRatio = self->cropAspectRatio;
    }
}

// Helper method to restore crop view state
- (void)restoreCropViewState {
    // This method is now primarily for restoring crop frame
    if (_hasValidSavedState && !CGRectIsEmpty(_savedCropFrame)) {
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(0.1 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
            [self restoreCropFrame];
        });
    }
}

// Helper method to setup crop view properties
- (void)setupCropView {
    _inlineCropView.autoresizingMask = UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
    _inlineCropView.frame = self.bounds;
    
    if (self->keepAspectRatio) {
        _inlineCropView.aspectRatioLockEnabled = keepAspectRatio;
    }
    if (self->iosDimensionSwapEnabled) {
        _inlineCropView.aspectRatioLockDimensionSwapEnabled = iosDimensionSwapEnabled;
    }
    if (!CGSizeEqualToSize(self->cropAspectRatio, CGSizeZero)) {
        _inlineCropView.aspectRatio = self->cropAspectRatio;
    }
    
    [_inlineCropView moveCroppedContentToCenterAnimated:NO];
    [_inlineCropView performInitialSetup];
    [self addSubview:_inlineCropView];
}

// Rotate image by degrees
- (UIImage *)rotateImageByDegrees:(UIImage *)image degrees:(CGFloat)degrees {
    CGFloat radians = degrees * (M_PI / 180.0);
    
    // Calculate the size of the rotated image
    CGRect rotatedRect = CGRectApplyAffineTransform(CGRectMake(0, 0, image.size.width, image.size.height),
                                                     CGAffineTransformMakeRotation(radians));
    CGSize rotatedSize = rotatedRect.size;
    
    // Create a context with the rotated size
    UIGraphicsBeginImageContextWithOptions(rotatedSize, NO, image.scale);
    CGContextRef context = UIGraphicsGetCurrentContext();
    
    // Move origin to center, rotate, then move back
    CGContextTranslateCTM(context, rotatedSize.width / 2, rotatedSize.height / 2);
    CGContextRotateCTM(context, radians);
    CGContextTranslateCTM(context, -image.size.width / 2, -image.size.height / 2);
    
    // Draw the image
    [image drawInRect:CGRectMake(0, 0, image.size.width, image.size.height)];
    
    UIImage *rotatedImage = UIGraphicsGetImageFromCurrentImageContext();
    UIGraphicsEndImageContext();
    
    return rotatedImage;
}

// Flip image horizontally or vertically
- (UIImage *)flipImage:(UIImage *)image horizontal:(BOOL)horizontal {
    UIGraphicsBeginImageContextWithOptions(image.size, NO, image.scale);
    CGContextRef context = UIGraphicsGetCurrentContext();
    
    if (horizontal) {
        // Horizontal flip: flip x-axis
        CGContextTranslateCTM(context, image.size.width, 0);
        CGContextScaleCTM(context, -1.0, 1.0);
    } else {
        // Vertical flip: flip y-axis
        CGContextTranslateCTM(context, 0, image.size.height);
        CGContextScaleCTM(context, 1.0, -1.0);
    }
    
    [image drawInRect:CGRectMake(0, 0, image.size.width, image.size.height)];
    
    UIImage *flippedImage = UIGraphicsGetImageFromCurrentImageContext();
    UIGraphicsEndImageContext();
    
    return flippedImage;
}

@end
