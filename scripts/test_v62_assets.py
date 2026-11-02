"""Runtime asset provenance and dimensions, using only Python's standard library."""
import hashlib,json,struct,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
ART=ROOT/'apps/web/public/art/v62'
class RuntimeAssets(unittest.TestCase):
 def test_original_runtime_hashes(self):
  manifest=json.loads((ART/'manifest.json').read_text())
  self.assertEqual(118,len(manifest['files']))
  for f in manifest['files']:
   with self.subTest(file=f['path']):
    data=(ART/f['path']).read_bytes();self.assertEqual(f['bytes'],len(data));self.assertEqual(f['sha256'],hashlib.sha256(data).hexdigest())
 def dimensions(self,path):
  data=(ART/path).read_bytes();self.assertEqual(b'\x89PNG\r\n\x1a\n',data[:8]);return struct.unpack('>II',data[16:24])
 def test_native_frames_and_animation_contract(self):
  for who in ['adult_female','adult_male','child']:self.assertEqual((96,160),self.dimensions('assets/characters/'+who+'.png'))
  self.assertEqual((80,64),self.dimensions('assets/pets/small_dog_walk.png'));self.assertEqual((40,64),self.dimensions('assets/pets/small_dog_states.png'))
  a=json.loads((ART/'animation.json').read_text());self.assertEqual(['down','left','right','up'],a['directions']);self.assertEqual([1,2,3,2],a['human']['walk']);self.assertEqual(150,a['human']['msPerFrame'])
 def test_shared_catalogue_is_the_original_runtime_map(self):
  data=json.loads((ROOT/'assets/space-catalog.json').read_text())
  for name,m in data['maps'].items():
   original=json.loads((ART/'maps'/f'{name}.runtime.json').read_text())
   self.assertEqual(original,m)
   self.assertEqual(560,len(m['collision']))
   for obj in m['objects']:self.assertTrue((ART/obj['sprite']).is_file())
if __name__=='__main__':unittest.main()
