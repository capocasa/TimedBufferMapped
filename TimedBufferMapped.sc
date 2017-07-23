
// This adds support for recording mapped controls

RecordBufM {
  *ar {
    thisMethod.notYetImplemented;
  }

  *kr {
    arg inputArray, bufspec=#[0], run=1.0, doneAction=0;

    if (inputArray.size != bufspec.size) {
      RecordBufMError("inputArray size is %, but bufspec size is %, need be equal".format(inputArray.size, bufspec.size)).throw;
    };

    inputArray = inputArray.copy;

    bufspec.do{|bufspecch, i|
      var rec, inputch, run, time;
      inputch = inputArray[i].copy;
      run = inputch[0] > 0;
      time = Sweep.kr(1,run>0);
      bufspecch[1..].pairsDo { |ch, b|
        RecordBufT.kr(inputch[ch], b, run:run);
        inputch[ch] = if(run,time,0);
      };
      inputArray[i] = inputch;
    };
    ^RecordBufT.kr(inputArray, bufspec.collect{|bufspecch|bufspecch[0]}, run, doneAction);
  }
}
RecordBufMError : Error {}

PlayBufM {
  *ar {
    thisMethod.notYetImplemented;
  }

  *kr {
    arg numChannels, bufspec = #[0], rate=1.0, trigger=1.0, startPos=0.0, doneAction=0;
    var play, out;

    out = PlayBufT.kr(numChannels, bufspec.collect{|bufspecch|bufspecch[0]}, rate, trigger, startPos, doneAction);

    bufspec.do { |bufspecch, i|
      var run;
      run = out[i][0] > 0;

      bufspecch[1..].pairsDo { |ch, b|
        var outch;
        outch = out[i].copy; // avoid buffer coloring error
        startPos = outch[ch];
        outch[ch] = PlayBufT.kr(1, b, rate * run, run, startPos) * run;
        out[i] = outch;
      };
    }
    ^out;
  }
}


+ Buffer {
  *allocMapped {
    arg server, polyphony, numChannels, channels = #[], frames = 16384, cframes = 16777216;
    var bufspec;
    bufspec = polyphony.collect {
      var bufspecch;
      bufspecch = Array.new(2*channels.size+1);
      bufspecch.add(Buffer.alloc(server, frames, numChannels+1));
      channels.do { |ch|
        bufspecch.add(ch);
        bufspecch.add(Buffer.alloc(server, cframes, 2));
      };
      bufspecch;
    };
    ^bufspec;
  }

  *readMapped {
    arg server, path;
    var bufspec, files, channels, buffers, sounds, polyphony, headerFormat, fileExtension,numChannels;
    files=PathName(path).files.select{|f|f.fileName[0]==$p}.postln;
    #polyphony, channels, fileExtension = files.first.fileName.split($.);
    channels = channels.split($-);
    headerFormat = fileExtension.toLower;
    polyphony = files.collect{|f|f.fileName[1..f.fileName.indexOf($.)-1].asInteger}.maxItem+1;

    [polyphony, channels, headerFormat].postln;
    /*channels = path +/this.loadChannels(this.meta(base)); 
    
    this.toSoundFile;
    bufspec=sounds.collect {|read|
      var write, line, spec, writeposch;
      read.openRead;
      write = SoundFile(read.path ++ ".ren");
      write.numChannels = numChannels;
      write.sampleFormat = "float";
      write.headerFormat = headerFormat;
      write.openWrite;

      line = FloatArray.newClear(read.numChannels);
      spec = channels.collect {|ch|
        var writech;
        writech = SoundFile(read.path ++ ".mapped."++ch);
        writech.headerFormat = headerFormat;
        writech.sampleFormat = "float";
        writech.openWrite;
        [ch, writech];
      }.flatten;
      writeposch = Array.fill(channels.maxItem+1, 0);
      while { read.readData(line); line.size > 0 }{
        var count, path, frames;
        spec.pairsDo { |ch, writech, i|
          count = line[ch+1];

          path = (base +/+ count ++ $.++$*).pathMatch.first;

          line[ch+1] = writeposch[ch];

          if (path.notNil) {
            SoundFile.use(path) { |readch|
              var chunk;
              frames = (line[0] * server.sampleRate/server.options.blockSize).ceil.max(readch.numFrames);
              chunk = FloatArray.newClear(16384);
              while { chunk.size > 0 }{
                readch.readData(chunk);
                writech.writeData(chunk);
              };
              writech.writeData(FloatArray.newClear(frames-readch.numFrames));
              writeposch.atInc(ch, frames);
              //[\frames, frames, frames - readch.numFrames, readch.numFrames].postln;
            };
          };
        };
        write.writeData(line);
      };
      read.close;
      write.close;
      "mv % %".format(write.path, read.path).systemCmd;
      spec.pairsDo {|ch, writech, i|
        writech.close;
        spec[i+1] = Buffer.read(server, writech.path);
      };
    };
    buffers = this.toBuffer(server);
    bufspec = bufspec.collect { |bufspecch, i| [buffers[i]]++bufspecch;};
    ^bufspec;
   */
  }
}


+ Array {
  writeMapped {
    arg path, headerFormat = "aiff";
    var buffer, base, count, server, soundExtension, channels;
    server=this[0][0].server;
    soundExtension = headerFormat.toLower;
    count = 1;

//6.postln;
    //if (frames.asArray.every({|e|e==1})) {
    //  "No frames, not saving %".format(path).warn;
    //  this.yield;
    //};
    path.mkdir;
//5.postln;
    channels = [];
    this[0][1..].pairsDo{|ch|channels=channels.add(ch)};
    this.do { |bufspecch, i|
      var read, write, pathch, tmp, line, id;
      fork {
//4.postln;
        pathch=path+/+"p"++i++$.++channels.join("-")++$.++soundExtension;
        tmp = thisProcess.platform.defaultTempDir +/+ "map" ++ 2147483647.rand++$.++soundExtension;
        bufspecch[0].writeTimed(tmp, headerFormat);
        server.sync;
        read = SoundFile(tmp);
        read.openRead;
        write = SoundFile(pathch);
        write.numChannels = read.numChannels;
        write.headerFormat = headerFormat;
        write.sampleFormat = "float";
        write.openWrite;
        line = FloatArray.newClear(read.numChannels);
//3.postln;
        id = 0;
        while { read.readData(line); line.size > 0 && { line[0] > 0 } } {
//2.postln;
          bufspecch[1..].pairsDo {|ch, b, x|
            var r, w, p, start;
            id = 2147483647.rand;
            if (line[1] > 0) {  // Don't save signals for pause notes
//9.postln;
//line.postln;
              start = line[ch+1]; // ch is output channel index, buffers have additional time channel at beginning, so ch+1
              p = path +/+ "v"++id ++ $. ++ soundExtension;
//"read polyphony % id % of length % to % with value %".format(i,id,line[0],p,line[ch+1]).postln;
              b.writeTimed(p, headerFormat, nil, start.asInteger);
              line[ch+1] = id;
//"id %".format(id).postln;
            };
            write.writeData(line);
          };
//1.postln;
        };
        server.sync; // Without this, longer passages crash the server
//7.postln;
        read.close;
        write.close;
        File.delete(tmp);
      };
//8.postln;

    }
  }
}


