/*
 * Copyright (C) 2024 hstr0100
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.brlns.gdownloader.util;

import java.util.Iterator;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public class LinkedIterableBlockingQueue<E> extends LinkedBlockingQueue<E>{

    @Override
    public Iterator<E> iterator(){
        synchronized(this){
            while(isEmpty()){
                try{
                    wait();
                }catch(InterruptedException e){
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted", e);
                }
            }
        }

        return super.iterator();
    }

    @Override
    public boolean offer(E e){
        boolean added = super.offer(e);

        if(added){
            synchronized(this){
                notifyAll();
            }
        }

        return added;
    }

    @Override
    public boolean remove(Object o){
        boolean removed;

        synchronized(this){
            removed = super.remove(o);
            if(removed){
                notifyAll();
            }
        }

        return removed;
    }
}
