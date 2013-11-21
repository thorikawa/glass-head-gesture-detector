Head Gesture Detector
===========

Google Glass specific library for head gesture detection.

### Usage
```
public class MainActivity extends Activity implements OnHeadGestureListener {

    private HeadGestureDetector mHeadGestureDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        …
        mHeadGestureDetector = new HeadGestureDetector(this);
        mHeadGestureDetector.setOnHeadGestureListener(this);
        …
    }

    @Override
    protected void onResume() {
        …
        mHeadGestureDetector.start();
    }

    @Override
    protected void onPause() {
        …
        mHeadGestureDetector.stop();
    }

    @Override
    public void onNod() {
        // Do something
    }
}
```

### License
```
Copyright 2013 Poly's Factory

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
