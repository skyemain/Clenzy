import json, pathlib, time
import numpy as np
import onnxruntime as ort
from tokenizers import Tokenizer
import argparse
parser=argparse.ArgumentParser(description="Regenerate category vectors with pinned SigLIP text encoder and tokenizer")
parser.add_argument("model_directory",type=pathlib.Path)
base=parser.parse_args().model_directory
root=pathlib.Path(__file__).resolve().parents[1]
prompts=json.load(open(root/'build/semantic-prompts.json'))
tok=Tokenizer.from_file(str(base/'tokenizer.json'))
tok.enable_padding(length=64,pad_id=0,pad_token='<pad>')
tok.enable_truncation(max_length=64)
session=ort.InferenceSession(str(base/'text.onnx'),providers=['CPUExecutionProvider'])
print('text',[(x.name,x.shape) for x in session.get_inputs()],[(x.name,x.shape) for x in session.get_outputs()],flush=True)
vectors=[]
for label,phrases in prompts.items():
 embeddings=[]
 for phrase in phrases:
  ids=np.array([tok.encode(phrase.lower()).ids],dtype=np.int64)
  output=session.run(['pooler_output'],{'input_ids':ids})[0][0]
  embeddings.append(output/np.linalg.norm(output))
 vector=np.mean(embeddings,axis=0);vector/=np.linalg.norm(vector)
 vectors.append({'label':label,'vector':[round(float(v),7) for v in vector]})
dest=root/'app/src/main/assets'
dest.mkdir(parents=True,exist_ok=True)
json.dump(vectors,open(dest/'semantic-vectors.json','w'),separators=(',',':'))
