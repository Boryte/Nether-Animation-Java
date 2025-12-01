# Nether-Animation-Java

I started playing with shader-style math and ended up with this simple “nether” animation written in plain Java.

The program:

- Renders a sequence of frames using a tiny CPU-side “shader”
- Writes them as **PPM** images (binary `P6` format)
- Calls **FFmpeg** to turn the frames into an MP4 video

I’m using PPM instead of PNG because:

- It’s extremely easy to write (tiny header + raw RGB bytes)
- No compression - > faster to generate
- Perfect for synthetic frames and feeding into FFmpeg

The only real downside is larger file size for the intermediate frames, but they’re temporary and easy to delete.

---

## Video preview 
<p align="center" width="100%">
  <video src="https://github.com/user-attachments/assets/05e65390-2cd2-4f5e-b770-345c3be378f7"
         width="80%"
         controls>
  </video>
</p>

