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
    UIImage * _currentTransformedImage; // Store current transformed image
    NSString * _lastSourceUrl; // Track source URL changes
    
    // State preservation variables
    CGFloat _savedAngle;
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
            _hasValidSavedState = NO; // Clear saved state for new image
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
    // Save current state before rotation
    [self saveCropViewState];
    
    [_inlineCropView rotateImageNinetyDegreesAnimated:YES clockwise:clockwise];
    
    // Update the current transformed image to match the rotation
    dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(0.1 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
        self->_currentTransformedImage = self->_inlineCropView.image;
        // Update saved state after the rotation animation completes
        [self saveCropViewState];
    });
}

// New methods for flipping and resetting with state preservation
- (void)flipImageHorizontally {
    if (_inlineCropView && _currentTransformedImage) {
        UIImage *flippedImage = [self flipImageHorizontallyUsingOrientation:_currentTransformedImage];
        _currentTransformedImage = flippedImage;
        
        // Remove current crop view and recreate with flipped image
        [_inlineCropView removeFromSuperview];
        _inlineCropView = [[TOCropView alloc] initWithImage:flippedImage];
        [self setupCropView];
        
        // Don't restore rotation state - the flip is now part of the image
        _hasValidSavedState = NO;
    }
}

- (void)flipImageVertically {
    if (_inlineCropView && _currentTransformedImage) {
        UIImage *flippedImage = [self flipImageVerticallyUsingDrawing:_currentTransformedImage];
        _currentTransformedImage = flippedImage;
        
        // Remove current crop view and recreate with flipped image
        [_inlineCropView removeFromSuperview];
        _inlineCropView = [[TOCropView alloc] initWithImage:flippedImage];
        [self setupCropView];
        
        // Don't restore rotation state - the flip is now part of the image
        _hasValidSavedState = NO;
    }
}

- (void)resetImage {
    if (_inlineCropView && _originalImage) {
        _currentTransformedImage = _originalImage; // Reset current state to original
        _hasValidSavedState = NO; // Clear saved state on reset
        
        // Remove current crop view and recreate with original image
        [_inlineCropView removeFromSuperview];
        _inlineCropView = [[TOCropView alloc] initWithImage:_originalImage];
        [self setupCropView];
    }
}

// Helper method to save current crop view state
- (void)saveCropViewState {
    if (_inlineCropView) {
        _savedAngle = _inlineCropView.angle;
        _hasValidSavedState = YES;
    }
}

// Helper method to restore crop view state
- (void)restoreCropViewState {
    if (_inlineCropView && _hasValidSavedState) {
        // Restore rotation by calculating the number of 90-degree rotations needed
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(0.1 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
            if (self->_inlineCropView && fabs(self->_savedAngle) > 0.001) {
                // Convert angle to number of 90-degree rotations
                int rotations = (int)round(self->_savedAngle / (M_PI_2));
                rotations = rotations % 4; // Ensure it's between 0-3
                if (rotations < 0) rotations += 4; // Handle negative angles
                
                // Apply the rotations
                for (int i = 0; i < rotations; i++) {
                    [self->_inlineCropView rotateImageNinetyDegreesAnimated:NO clockwise:YES];
                }
            }
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

// Horizontal flip using UIImage orientation (works well for mirroring)
- (UIImage *)flipImageHorizontallyUsingOrientation:(UIImage *)image {
    UIImageOrientation newOrientation;
    
    switch (image.imageOrientation) {
        case UIImageOrientationUp:
            newOrientation = UIImageOrientationUpMirrored;
            break;
        case UIImageOrientationDown:
            newOrientation = UIImageOrientationDownMirrored;
            break;
        case UIImageOrientationLeft:
            newOrientation = UIImageOrientationRightMirrored;
            break;
        case UIImageOrientationRight:
            newOrientation = UIImageOrientationLeftMirrored;
            break;
        case UIImageOrientationUpMirrored:
            newOrientation = UIImageOrientationUp;
            break;
        case UIImageOrientationDownMirrored:
            newOrientation = UIImageOrientationDown;
            break;
        case UIImageOrientationLeftMirrored:
            newOrientation = UIImageOrientationLeft;
            break;
        case UIImageOrientationRightMirrored:
            newOrientation = UIImageOrientationRight;
            break;
        default:
            newOrientation = UIImageOrientationUpMirrored;
            break;
    }
    
    return [[UIImage alloc] initWithCGImage:image.CGImage scale:image.scale orientation:newOrientation];
}

// Vertical flip using hybrid approach: horizontal flip + 180 rotation + horizontal flip
- (UIImage *)flipImageVerticallyUsingDrawing:(UIImage *)image {
    // To get a pure vertical flip, we can do: horizontal flip + 180° rotation + horizontal flip
    // This results in just a vertical flip
    
    // Step 1: Horizontal flip
    UIImage *step1 = [self flipImageHorizontallyUsingOrientation:image];
    
    // Step 2: 180-degree rotation (which is vertical flip + horizontal flip)
    UIImage *step2 = [[UIImage alloc] initWithCGImage:step1.CGImage 
                                                scale:step1.scale 
                                          orientation:UIImageOrientationDown];
    
    // Step 3: Horizontal flip again (to cancel out the horizontal component from step 2)
    UIImage *step3 = [self flipImageHorizontallyUsingOrientation:step2];
    
    // Step 4: Render the final result
    UIGraphicsBeginImageContextWithOptions(image.size, NO, image.scale);
    [step3 drawInRect:CGRectMake(0, 0, image.size.width, image.size.height)];
    UIImage *finalImage = UIGraphicsGetImageFromCurrentImageContext();
    UIGraphicsEndImageContext();
    
    return finalImage;
}

@end
