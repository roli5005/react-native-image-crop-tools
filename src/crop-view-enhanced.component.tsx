import React, { createRef, useState } from 'react';
import { View, Text, TouchableOpacity, Modal, FlatList, StyleSheet, SafeAreaView, Platform } from 'react-native';
import CropView from './crop-view.component';

export type AspectRatio = {
  label: string;
  value: Ratio;
};

type Ratio = {
  width: number;
  height: number;
} | null;

// Define aspect ratio presets
const ASPECT_RATIOS: AspectRatio[] = [
  { label: 'Free', value: null },
  { label: 'Square (1:1)', value: { width: 1, height: 1 } },
  { label: '4:3', value: { width: 4, height: 3 } },
  { label: '3:4', value: { width: 3, height: 4 } },
  { label: '16:9', value: { width: 16, height: 9 } },
  { label: '9:16', value: { width: 9, height: 16 } },
  { label: '3:2', value: { width: 3, height: 2 } },
  { label: '2:3', value: { width: 2, height: 3 } },
  { label: '5:4', value: { width: 5, height: 4 } },
  { label: '4:5', value: { width: 4, height: 5 } },
  { label: 'Instagram Portrait (4:5)', value: { width: 4, height: 5 } },
  { label: 'Instagram Landscape (1.91:1)', value: { width: 191, height: 100 } },
  { label: 'Facebook Cover (16:9)', value: { width: 16, height: 9 } },
  { label: 'Twitter Header (3:1)', value: { width: 3, height: 1 } },
];

type AspectRatioSelectorProps = {
  selectedRatio: Ratio;
  onSelectRatio: (ratio: Ratio) => void;
  customAspectRatios?: AspectRatio[];
};

// Aspect Ratio Selector Component
const AspectRatioSelector: React.FC<AspectRatioSelectorProps> = ({
  selectedRatio,
  onSelectRatio,
  customAspectRatios,
}) => {
  const [modalVisible, setModalVisible] = useState(false);

  const getCurrentLabel = () => {
    if (!selectedRatio) return 'Free';

    if (customAspectRatios !== undefined) {
      const found = customAspectRatios.find(
        item => item.value && item.value.width === selectedRatio.width && item.value.height === selectedRatio.height
      );
      return found ? found.label : `${selectedRatio.width}:${selectedRatio.height}`;
    }

    const found = ASPECT_RATIOS.find(
      item => item.value && item.value.width === selectedRatio.width && item.value.height === selectedRatio.height
    );

    return found ? found.label : `${selectedRatio.width}:${selectedRatio.height}`;
  };

  const handleSelect = (ratio: { width: number; height: number } | null) => {
    onSelectRatio(ratio);
    setModalVisible(false);
  };

  return (
    <>
      <TouchableOpacity style={styles.selectorButton} onPress={() => setModalVisible(true)} activeOpacity={0.7}>
        <Text style={styles.selectorButtonText}>Aspect Ratio: {getCurrentLabel()}</Text>
        <Text style={styles.selectorArrow}>▼</Text>
      </TouchableOpacity>

      <Modal
        animationType="slide"
        transparent={true}
        visible={modalVisible}
        onRequestClose={() => setModalVisible(false)}
      >
        <TouchableOpacity style={styles.modalOverlay} activeOpacity={1} onPress={() => setModalVisible(false)}>
          <View style={styles.modalContent}>
            <View style={styles.modalHeader}>
              <Text style={styles.modalTitle}>Select Aspect Ratio</Text>
              <TouchableOpacity onPress={() => setModalVisible(false)} style={styles.closeButton}>
                <Text style={styles.closeButtonText}>✕</Text>
              </TouchableOpacity>
            </View>

            <FlatList
              data={customAspectRatios ?? ASPECT_RATIOS}
              keyExtractor={item => item.label}
              renderItem={({ item }) => {
                const isSelected =
                  (!item.value && !selectedRatio) ||
                  (item.value &&
                    selectedRatio &&
                    item.value.width === selectedRatio.width &&
                    item.value.height === selectedRatio.height);

                return (
                  <TouchableOpacity
                    style={[styles.ratioItem, isSelected && styles.ratioItemSelected]}
                    onPress={() => handleSelect(item.value)}
                  >
                    <Text style={[styles.ratioItemText, isSelected && styles.ratioItemTextSelected]}>{item.label}</Text>
                    {isSelected && <Text style={styles.checkmark}>✓</Text>}
                  </TouchableOpacity>
                );
              }}
            />
          </View>
        </TouchableOpacity>
      </Modal>
    </>
  );
};

// Enhanced CropView with Aspect Ratio Selector
type EnhancedCropViewProps = {
  sourceUrl: string;
  style?: any;
  onImageCrop?: (res: any) => void;
  keepAspectRatio?: boolean;
  iosDimensionSwapEnabled?: boolean;
  showAspectRatioSelector?: boolean;
  defaultAspectRatio?: Ratio;
  customAspectRatios?: AspectRatio[];
};

const EnhancedCropView: React.FC<EnhancedCropViewProps> = ({
  sourceUrl,
  style,
  onImageCrop,
  keepAspectRatio = false,
  iosDimensionSwapEnabled = false,
  showAspectRatioSelector = true,
  defaultAspectRatio = null,
  customAspectRatios,
}) => {
  const cropViewRef = createRef<CropView>();
  const [aspectRatio, setAspectRatio] = useState<{ width: number; height: number } | null>(defaultAspectRatio);

  const handleAspectRatioChange = (ratio: { width: number; height: number } | null) => {
    setAspectRatio(ratio);
  };

  const handleSaveImage = (preserveTransparency: boolean = true, quality: number = 90) => {
    cropViewRef.current?.saveImage(preserveTransparency, quality);
  };

  const handleRotateImage = (clockwise: boolean = true) => {
    cropViewRef.current?.rotateImage(clockwise);
  };

  const handleFlipHorizontally = () => {
    cropViewRef.current?.flipImageHorizontally();
  };

  const handleFlipVertically = () => {
    cropViewRef.current?.flipImageVertically();
  };

  const handleResetImage = () => {
    cropViewRef.current?.resetImage();
  };

  return (
    <View style={styles.container}>
      {showAspectRatioSelector && (
        <View style={styles.selectorContainer}>
          <AspectRatioSelector
            selectedRatio={aspectRatio}
            onSelectRatio={handleAspectRatioChange}
            customAspectRatios={customAspectRatios}
          />
        </View>
      )}

      <CropView
        ref={cropViewRef}
        sourceUrl={sourceUrl}
        style={[styles.cropView, style]}
        onImageCrop={onImageCrop}
        keepAspectRatio={keepAspectRatio || !!aspectRatio}
        aspectRatio={aspectRatio || undefined}
        iosDimensionSwapEnabled={iosDimensionSwapEnabled}
      />

      {/* Optional: Add control buttons */}
      <View style={styles.controlsContainer}>
        <TouchableOpacity style={styles.controlButton} onPress={() => handleRotateImage(true)}>
          <Text style={styles.controlButtonText}>Rotate ↻</Text>
        </TouchableOpacity>

        <TouchableOpacity style={styles.controlButton} onPress={handleFlipHorizontally}>
          <Text style={styles.controlButtonText}>Flip H</Text>
        </TouchableOpacity>

        <TouchableOpacity style={styles.controlButton} onPress={handleFlipVertically}>
          <Text style={styles.controlButtonText}>Flip V</Text>
        </TouchableOpacity>

        <TouchableOpacity style={styles.controlButton} onPress={handleResetImage}>
          <Text style={styles.controlButtonText}>Reset</Text>
        </TouchableOpacity>

        <TouchableOpacity style={[styles.controlButton, styles.saveButton]} onPress={() => handleSaveImage()}>
          <Text style={styles.controlButtonText}>Save</Text>
        </TouchableOpacity>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  selectorContainer: {
    paddingHorizontal: 16,
    paddingVertical: 12,
    backgroundColor: '#fff',
    borderBottomWidth: 1,
    borderBottomColor: '#e0e0e0',
  },
  selectorButton: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 12,
    paddingHorizontal: 16,
    backgroundColor: '#f5f5f5',
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#ddd',
  },
  selectorButtonText: {
    fontSize: 16,
    color: '#333',
    fontWeight: '500',
  },
  selectorArrow: {
    fontSize: 12,
    color: '#666',
  },
  modalOverlay: {
    flex: 1,
    backgroundColor: 'rgba(0, 0, 0, 0.5)',
    justifyContent: 'flex-end',
  },
  modalContent: {
    backgroundColor: '#fff',
    borderTopLeftRadius: 20,
    borderTopRightRadius: 20,
    maxHeight: '70%',
    paddingBottom: Platform.OS === 'ios' ? 34 : 20,
  },
  modalHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: 16,
    borderBottomWidth: 1,
    borderBottomColor: '#e0e0e0',
  },
  modalTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#333',
  },
  closeButton: {
    padding: 4,
  },
  closeButtonText: {
    fontSize: 24,
    color: '#666',
  },
  ratioItem: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 14,
    paddingHorizontal: 20,
    borderBottomWidth: 1,
    borderBottomColor: '#f0f0f0',
  },
  ratioItemSelected: {
    backgroundColor: '#f0f8ff',
  },
  ratioItemText: {
    fontSize: 16,
    color: '#333',
  },
  ratioItemTextSelected: {
    fontWeight: '600',
    color: '#007AFF',
  },
  checkmark: {
    fontSize: 18,
    color: '#007AFF',
    fontWeight: '600',
  },
  cropView: {
    flex: 1,
  },
  controlsContainer: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    paddingVertical: 12,
    paddingHorizontal: 8,
    backgroundColor: '#fff',
    borderTopWidth: 1,
    borderTopColor: '#e0e0e0',
  },
  controlButton: {
    paddingVertical: 8,
    paddingHorizontal: 12,
    backgroundColor: '#f5f5f5',
    borderRadius: 6,
    borderWidth: 1,
    borderColor: '#ddd',
  },
  saveButton: {
    backgroundColor: '#007AFF',
    borderColor: '#007AFF',
  },
  controlButtonText: {
    fontSize: 14,
    color: '#333',
    fontWeight: '500',
  },
});

export default EnhancedCropView;
