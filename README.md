# PaintScan
[InventionWeek2020] Paint, scan, and see your own creations in Minecraft!
Please contact seme@microsoft or rdaustin@microsoft.com  if you have any questions.

## Next steps ##
1. Making photo capture easy and auto-align. We were really close on this, but we just demoed a version with manual-editing. We essentially need to track and marker/image or do a shape detection (a solid black rectangle for example); and apply rotation transform to larger image.

2. AR Render. Task would involve somehow loading geo.json game models with correct texture UV applied. BUT one of the hackathons was transforming geo.json models into FBX. Which would make rendering easier. Don't know about UV textures. Shaun Foley from Skybox built the FBX Converter.

3. Creating a paper-model of game models, and remapping this texture to game texture. This is non-trivial.

    * I think first piece can be done by loading the model in a 3D Modeler software (or a special purpose tool) that would allow us to "unwrap" polygons/textures. I believe there are extensions for it. End result would look like this: http://www.pixelpapercraft.com/papercraft/519aa3bf989bdc817c000001/steve-very-easy
     
     * Then, these UV's need to be mapped to MinecraftPE's ugly texture format.
