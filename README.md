# react-native-image-crop-tools

# Enhanced Crop View Library

This is a fork of [hhunaid/react-native-image-crop-tools](https://github.com/hhunaid/react-native-image-crop-tools) by Hunaid Hassan with additional functionality.

## What's New
- Added `flipImageHorizontally()` method
- Added `flipImageVertically()` method
- Added `resetImage()` method
- Added proper transformation chains
- Added prebuilt UI
- Custom list of aspect ratios for prebuilt UI component

## Credits
Original library created by [Hunaid Hassan](https://github.com/hhunaid).
Enhanced by Soos Roland.


## Previews
<p float="left">
  <img src="https://github.com/roli5005/react-native-image-crop-tools/blob/master/previews/android-preview.gif?raw=true" width="150" />
  <img src="https://github.com/roli5005/react-native-image-crop-tools/blob/master/previews/ios-preview.gif?raw=true" width="150" /> 
</p>

## Getting started

`$ npm install @roli5005/react-native-image-crop-tools@latest`

### Automatic installation

Only RN > 0.61.x is supported.
- Android: Installation is automatic.
- iOS: Run `pod install`in `ios` folder.
   
### Why another cropping library?

Most cropping tools available for RN are usually wrappers over popular native tools which itself isn't a bad thing. But this means you are stuck with their UI and feature set. The ones made in RN are not the most optimized and correct tools.

## Features

1. Native views. Which means performance even on low end devices.
2. You can embed the view into you own UI. It's not very customizable (yet)
3. Change and lock/unlock aspect ratio on the fly (This is the main reason I am making this library)
4. Flip image vertically and horizontally.
5. Image transformations (rotate, flip) now properly compound and preserve their cumulative effect - for example, rotating left then flipping horizontally keeps the image rotated while flipping its content, rather than resetting to the original orientation.
6. Added new component EnhancedCropView, that has aspect ratio selector and image transformation UI components.
7. Added new field: customAspectRatios, where you can add your own list of aspect ratios that you wan to use.

# NOTE

This library is not supposed to work directly with remote images. There are very few usecases for that. You need to provide a sourceUrl string for a local file which you can obtain from image pickers or by downloading a remote file with rn-fetch-blob

## Usage
```javascript
import { CropView } from 'react-native-image-crop-tools';

        <CropView
          sourceUrl={uri}
          style={styles.cropView}
          ref={cropViewRef}
          onImageCrop={(res) => console.warn(res)}
          keepAspectRatio
          aspectRatio={{width: 16, height: 9}}
        />
```
```javascript
import { EnhancedCropView } from 'react-native-image-crop-tools'

      const customAspectRatios: AspectRatio[] = [
        { label: 'Free', value: null }, // if value is null, the crop size is draggable freely
        { label: 'My Custom Aspect Ratio', value: { width: 1, height: 1 } },
      ];

      <EnhancedCropView
        sourceUrl={uri}
        style={styles.cropView}
        ref={cropViewRef}
        onImageCrop={(res) => console.warn(res)}
        keepAspectRatio
        aspectRatio={{width: 16, height: 9}}
        customAspectRatios={customAspectRatios}
      />

```

The following methods are exposed on the ref. You can use them as follows

```javascript
  cropViewRef.current?.saveImage(true, 100); // image quality percentage)
  cropViewRef.current?.rotateImage(true);// true for clockwise, false for counterclockwise)
  cropViewRef.current?.flipImageHorizontally();
  cropViewRef?.current?.flipImageVertically();
  cropViewRef.current?.resetImage();
```

### Props

| Name | Description | Default |
| ---- | ----------- | ------- |
| sourceUrl | URL of the source image | `null` |
| aspectRatio | Aspect ratio of the cropped image | `null` |
| keepAspectRatio | Locks the aspect ratio to given aspect ratio | `false` |
| customAspectRatios | (EnhancedCropView only) (Optional) Replaces the default list of aspect ratios  | `undefined` |
| iosDimensionSwapEnabled | (iOS Only) Swaps the width and height of the crop rectangle upon rotation | `false` |

