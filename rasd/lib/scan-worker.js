/* Rasd: barcode decoding off the main thread, so the camera preview stays smooth */
importScripts('zxing-reader.js');
ZXingWASM.prepareZXingModule({
  overrides: { locateFile: (p, pre) => p.endsWith('.wasm') ? 'zxing_reader.wasm' : pre + p },
  fireImmediately: true
});
let cv = null, cx = null;
onmessage = async (e) => {
  const { id, bmp, opts } = e.data;
  let text = null;
  try {
    if (!cv || cv.width !== bmp.width || cv.height !== bmp.height) {
      cv = new OffscreenCanvas(bmp.width, bmp.height);
      cx = cv.getContext('2d', { willReadFrequently: true });
    }
    cx.drawImage(bmp, 0, 0);
    const img = cx.getImageData(0, 0, bmp.width, bmp.height);
    const r = await ZXingWASM.readBarcodes(img, opts);
    const g = r.find(x => x.isValid && x.text);
    text = g ? g.text : null;
  } catch (err) { text = null; }
  try { bmp.close(); } catch (err) {}
  postMessage({ id, text });
};
